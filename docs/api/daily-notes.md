# Daily Notes API (Member 2)
All endpoints require a valid caregiver bearer token. Owner identity is derived from the verified JWT; clients never submit caregiver ID. Another caregiver's resources return 404.
## Create
POST /api/notes; optional Idempotency-Key header. JSON requires recipientId and date. The canonical `observations` object uses per-field states (`RECORDED`, `UNKNOWN`, `NOT_RECORDED`, `NOT_APPLICABLE`); `medicationVerification` is separate (`VERIFIED`, `UNVERIFIED`, `NOT_ASSERTED`). Legacy flat fields remain accepted for compatibility. Free text is preserved verbatim including Arabic/mixed-language. One note per recipient per date.
## Read
GET /api/notes/{id}; GET /api/notes/by-recipient/{recipientId}?date=YYYY-MM-DD. Detail reads return the immutable original note plus an `addenda` array in chronological order and `addendumCount`. History pages include `addendumCount` and `correctionCount`, not event bodies.
## History
GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&recipientId=<optional UUID>&page=0&size=20. Inclusive dates; newest first with stable ID tiebreaker; page size max 100; addendum and structured-correction counts included, not bodies. Filtering by a recipient that is not owned returns an empty page (no existence leak).
## Addenda
POST /api/notes/{id}/addenda with optional Idempotency-Key; corrections append only and never modify original note. An addendum must correct at least one field (empty `{}` is rejected). GET /api/notes/{id}/addenda returns addenda ordered by creation time, with ID as a stable tiebreaker.
These existing records remain compatible and are not automatically reclassified as structured events.
## Structured correction events
`POST /api/notes/{id}/corrections` appends typed, immutable correction events. `GET /api/notes/{id}/corrections` returns the unchanged original note, all structured events in sequence order, and a replayed `effectiveObservations` map. The API is caregiver-scoped; a note belonging to another caregiver returns 404.

The request is a non-empty ordered list. A field absent from the list is unchanged; only an explicit operation changes that field. Requests may contain more than one event for a field, and each is retained and applied in list order.

```json
{
  "corrections": [
    {"field":"painScore","operation":"SET","value":0},
    {"field":"fallReported","operation":"SET","value":false},
    {"field":"appetite","operation":"UNKNOWN"},
    {"field":"sleep","operation":"CLEAR"},
    {"field":"mobility","operation":"NOT_RECORDED"}
  ]
}
```

`SET` requires a non-null value of the field's existing type. Text fields accept an empty string; pain is an integer from 0 through 10; booleans remain booleans; medication status is `TAKEN`, `MISSED`, or `UNKNOWN`. `CLEAR`, `UNKNOWN`, and `NOT_RECORDED` must not include a value. JSON `null` is not used to mean omission or clearing. Omission creates no event and leaves the effective observation unchanged.

Supported correction fields are the canonical structured observations: `mood`, `appetite`, `sleep`, `mobility`, `medicationStatus`, `painScore`, `fallReported`, and `freeText`. Medication verification is a separate verification state and is not a structured observation correction field. Values are validated against the Issue #67 observation contract. In particular, a recorded medication status is `TAKEN` or `MISSED`; unknown is represented by the `UNKNOWN` operation, not by `SET("UNKNOWN")`. Unsupported names, operations, and incorrectly typed values receive 400.

Both correction endpoints return this response shape (abbreviated, with one event):

```json
{
  "originalNote": {"id":"<note UUID>","mood":"good","painScore":3,"addendumCount":1,"correctionCount":1,"observations":{"mood":{"state":"RECORDED","value":"good"},"painScore":{"state":"RECORDED","value":3}}},
  "events": [{
    "id":"<event UUID>","noteId":"<note UUID>","sequenceNo":1,
    "createdAt":"2026-11-03T12:00:00Z","actorId":"<caregiver UUID>",
    "field":"painScore","operation":"SET","value":0,
    "previousEffective":{"state":"RECORDED","value":3,"lastOperation":null},
    "effectiveAfter":{"state":"RECORDED","value":0,"rawValue":null,"lastOperation":"SET"}
  }],
  "effectiveObservations": {"painScore":{"state":"RECORDED","value":0,"rawValue":null,"lastOperation":"SET"}}
}
```

`effectiveObservations[field]` uses the canonical observation states (`RECORDED`, `UNKNOWN`, `NOT_RECORDED`, `NOT_APPLICABLE`, `LEGACY_AMBIGUOUS`) and preserves a legacy raw value where available. `CLEAR` produces effective state `NOT_RECORDED` and retains `lastOperation: "CLEAR"`, so it remains distinguishable from an explicit `NOT_RECORDED` event. `SET` with false, zero, or an empty string is a `RECORDED` value. Legacy ambiguous observations retain their state and raw value until an explicit correction.

Each event includes its UUID, note ID, monotonically increasing per-note `sequenceNo`, timestamp, authenticated caregiver `actorId`, field, operation, and typed value when set. `previousEffective` and `effectiveAfter` show the field state before and after that event. Sequence is allocated while locking the note row; replay uses ascending sequence, independent of timestamp ties. Corrections are transactionally inserted with their batch and audit event. JPA entities and repositories expose no update/delete operation for event records; foreign keys use `ON DELETE RESTRICT`.

An optional `Idempotency-Key` applies to the whole correction batch. The unique caregiver/key record stores a SHA-256 request digest. Same-key/same-request retries create no events; same-key/different-request reuse returns 409. The correction migration adds `note_correction_batches` and `note_correction_events` in V8 after the explicit observation-state migration V7; it does not rewrite notes or existing addenda. Production migration execution is an operational deployment step and was not run as part of this implementation.
## Idempotency
Repeating a request with the same key and identical payload returns the original record (status 201; compare bodies to detect a replay) without creating duplicates. Concurrent duplicates serialize on the database unique constraint and collapse to a single row. Reusing a key with a different payload, or against a different note for addenda, returns 409 IDEMPOTENCY_CONFLICT. Keys longer than 100 characters are rejected with 400; blank keys carry no idempotency.
## Signals
GET /api/notes/signals?recipientId=<UUID>&from=YYYY-MM-DD&to=YYYY-MM-DD. Chronological signals; latest non-null addendum value wins per field. The response preserves the reported mood, appetite, sleep, mobility, and merged medication-status strings verbatim (contradictory medication reports are exposed as UNKNOWN) alongside the safety-layer projection (`medicationStatus`, `fallReported`, `painScore`, `missedMedication`, `medicationUnverified`, `poorAppetite`, `freeText`). Missing observations remain absent. Contradictory TAKEN and MISSED becomes UNKNOWN. Unverified medication is marked. Free text passes through and must not be interpreted as instructions.
### Medication contradiction policy
History is never rewritten: the original note and every addendum stay persisted. The interpreted signal, however, must not present uncertain information as a confirmed fact. When the combined same-day history contains both TAKEN and MISSED, the signal collapses to `missedMedication=null` with `medicationUnverified=true`, symmetrically in both directions (MISSED→TAKEN and TAKEN→MISSED corrections). A bare UNKNOWN report only leaves the state unverified. Downstream safety rules must treat `medicationUnverified=true` as "do not assert".
## Auditing
Successful writes record audit events in the same transaction: NOTE_CREATED (target DAILY_NOTE), NOTE_ADDENDUM_CREATED (target NOTE_ADDENDUM), and NOTE_CORRECTION_CREATED (target NOTE_CORRECTION_BATCH). Metadata carries identifiers and event count only; observation values and free-text contents are never written to audit logs.
## Errors
All error bodies use `{timestamp, status, code, message}` (plus `fields` for body validation). Validation errors: 400 VALIDATION_ERROR with field details. Invalid requests: 400 INVALID_REQUEST. Missing query parameters: 400 MISSING_PARAMETER. Malformed UUIDs/dates: 400 INVALID_PARAMETER. Inaccessible resources: 404 NOT_FOUND. Duplicate recipient/date: 409 NOTE_CONFLICT. Key reuse with a different payload: 409 IDEMPOTENCY_CONFLICT. Authentication failures: 401 UNAUTHORIZED (never folded into validation errors). Unexpected failures: 500 SERVER_ERROR without stack traces. Never expose other caregivers' data.
