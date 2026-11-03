# WP-02 Preflight — Recipient, Note, Observation, Addendum, and Date Integrity

**Document status:** Preflight and preparation only. No implementation authorization.
**Audit date:** 2026-11-03
**Repository:** alaa157/caregiver-daily-notes
**Web/backend branch:** main @ `6f715b57a6b4f67f3823301f6c8da86c834841e2`
**Android reference branch:** feat/standalone-byok-ai @ `21876285ad808a6518593ea3974c51b6b1f6bb31`
**Auditor role:** Senior software architect / data-integrity auditor (read-only)

---

## 1. Executive summary

WP-02 preflight establishes whether existing Web data and schema can be migrated safely toward the WP-01-approved semantic contracts (PO-01–PO-06) without inventing clinical meaning or destroying historical records.

**Key findings (evidence-based):**

- **Recipient lifecycle:** Web uses a boolean `active` (default true) with soft-deactivate only. Android uses `active` + nullable `archivedAt`. Mapping of historical `active=false` to canonical `ARCHIVED` is feasible but requires inventory of any records that might represent other states. No hard-delete path exists on Web. Relationship field already present and nullable on Web (CONFIRMED IN SOURCE).
- **Note identity / PO-04:** Schema has had `UNIQUE (recipient_id, note_date)` since the table was created (V5). Idempotency keys and service-layer recovery prevent most duplicates. Production duplicate count is **NOT VERIFIED** (no production dataset inspected). Schema prevents new collisions; any pre-constraint legacy data (if any) is unknown.
- **Observation semantics / PO-05:** Web stores nullable `Boolean`/`Integer`/`String` columns. Null is preserved in persistence. No code path that converts null → false or null → 0 was found in the inspected create/update paths. Explicit observation states (`RECORDED`/`UNKNOWN`/`NOT_RECORDED`/`NOT_APPLICABLE`/`LEGACY_AMBIGUOUS`) and separate verification are **not yet implemented**. Android note cache currently uses non-null primitives/strings for many fields, creating a representation gap.
- **Addenda / PO-06:** Web supports structured field overlays via `NoteAddendum` (same nullable fields as the original note). Original note columns are `updatable = false`. Android currently stores text-only addenda. Full immutable typed events with explicit `SET`/`CLEAR`/`UNKNOWN`/`NOT_RECORDED` masks are not present on either platform.
- **Date integrity:** `note_date` is `LocalDate` / PostgreSQL `DATE`. No Instant/UTC conversion of the calendar date itself was observed in the core persistence path. Boundary tests around timezone midnight shifts are still required.
- **Production data preflight:** Incomplete. No authorized production or staging dataset was available. All duplicate and legacy-count claims below are therefore limited to schema, source, and synthetic/test fixtures.

**Readiness outcome:** **NOT READY — DATA OR DECISION BLOCKERS REMAIN**

Primary blockers: production/staging duplicate inventory (or explicit waiver), approved mapping policy for existing inactive recipients, explicit preservation policy for null/legacy observation values, and reviewed migration plan with recovery strategy.

---

## 2. Scope and authorization boundary

This document is the WP-02 **preflight and preparation** deliverable only.

**Authorized:**

- Read-only inspection of Web (`main`) and Android (`feat/standalone-byok-ai`) source, entities, repositories, services, APIs, Flyway migrations, and tests.
- Read-only analysis of available test fixtures and schema.
- Documentation of risks, gaps, proposed migration sequence (not executed), and synthetic fixture catalogue.
- Production of this report under `docs/product-alignment/web-parity-roadmap/wp-02/`.

**Explicitly NOT authorized / not performed:**

- Any modification of application source, entities, DTOs, services, controllers, or frontend.
- Creation or execution of Flyway migrations.
- Addition/removal of constraints or indexes.
- Data backfills, cleanup, merges, deletions, or rewrites of records.
- Changes to Android source.
- Implementation of any Android-to-Web transfer protocol.
- Commits, pushes, or pull requests.

No application source, database schema, migration, or production data was changed.

---

## 3. Repository / branch / commit baseline

```
| Item                       | Value                                                                                              |
| -------------------------- | -------------------------------------------------------------------------------------------------- |
| Working tree               | Clean clone of repository (local path used for inspection only)                                    |
| Web/backend HEAD           | `main` @ `6f715b57a6b4f67f3823301f6c8da86c834841e2` (“docs: remove stale WP-01 approval language”) |
| Android reference HEAD     | `feat/standalone-byok-ai` @ `21876285ad808a6518593ea3974c51b6b1f6bb31`                             |
| Roadmap / WP-01 files      | Present on `main`; content matches the versions inspected for this audit                           |
| Pre-existing local changes | None introduced by this audit                                                                      |
```

Exact commits above are the audit baseline. Historical commit SHAs mentioned in older planning documents were not substituted for current inspection.

---

## 4. Source files inspected (selected)

**Web / backend (main):**

- `backend/src/main/java/com/caregiver/recipient/CareRecipient.java`
- `backend/src/main/java/com/caregiver/recipient/RecipientService.java`
- `backend/src/main/java/com/caregiver/recipient/RecipientController.java`
- `backend/src/main/java/com/caregiver/recipient/CareRecipientRepository.java`
- `backend/src/main/java/com/caregiver/notes/DailyNote.java`
- `backend/src/main/java/com/caregiver/notes/NoteAddendum.java`
- `backend/src/main/java/com/caregiver/notes/DailyNoteService.java`
- `backend/src/main/java/com/caregiver/notes/NoteRequest.java`
- `backend/src/main/java/com/caregiver/notes/NoteController.java`
- `backend/src/main/resources/db/migration/V2__create_care_recipients.sql`
- `backend/src/main/resources/db/migration/V5__create_daily_notes_and_addenda.sql`
- Related tests under `backend/src/test/java/com/caregiver/recipient/` and `.../notes/`

**Android (feat/standalone-byok-ai):**

- `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumEntity.kt`
- Related repositories, DAOs, and tests

**Authoritative product documents (mandatory review completed):**

- `docs/product-alignment/phase-1/README.md`
- `docs/product-alignment/phase-2/README.md`
- `docs/product-alignment/phase-3a/README.md`
- `docs/product-alignment/web-parity-roadmap/README.md`
- `docs/product-alignment/web-parity-roadmap/wp-01/README.md` (governing decision record)

WP-01 statuses for PO-01–PO-06 are treated as authoritative; Phase 2 recommendations do not override them.

---

## 5. Current Web recipient model and lifecycle

**Entity / table:** `CareRecipient` / `care_recipients` (V2 + entity annotations).

```
| Aspect               | Evidence                                                                                             | Label                                                |
| -------------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Primary key          | UUID, `@UuidGenerator`, non-updatable                                                                | CONFIRMED IN SOURCE                                  |
| Ownership            | `caregiver_id` FK, required, non-updatable                                                           | CONFIRMED IN SOURCE                                  |
| Name                 | `VARCHAR(120)` NOT NULL, blank-check constraint                                                      | CONFIRMED IN SOURCE                                  |
| Relationship         | `VARCHAR(80)` nullable (already present)                                                             | CONFIRMED IN SOURCE (PO-01 already satisfied on Web) |
| Active flag          | `boolean active` NOT NULL DEFAULT TRUE                                                               | CONFIRMED IN SOURCE                                  |
| Soft deactivate      | `deactivate()` sets `active = false`; comment states related notes/plans are not cascaded or deleted | CONFIRMED IN SOURCE (`CareRecipient.java`)           |
| Restore / reactivate | No method or endpoint found                                                                          | CONFIRMED IN SOURCE (gap)                            |
| Hard delete          | No delete path; FK ON DELETE RESTRICT                                                                | CONFIRMED IN SOURCE                                  |
| List behavior        | `findAllByCaregiver_IdOrderByCreatedAtDesc` — includes inactive                                      | CONFIRMED IN SOURCE                                  |
| Update guard         | Inactive recipients cannot be updated (`InactiveRecipientException`)                                 | CONFIRMED IN SOURCE                                  |
| Create/note guard    | Inactive recipient rejected on note create                                                           | CONFIRMED IN SOURCE (`DailyNoteService`)             |
| Indexes              | `(caregiver_id)`, `(caregiver_id, active)`                                                           | CONFIRMED IN SOURCE                                  |
```

**Lifecycle risk summary:** Soft-deactivate preserves child records (desired). Absence of restore and of an explicit `DELETED` state means the approved canonical lifecycle (`ACTIVE` / `ARCHIVED` / `DELETED`) is not yet represented. Existing `active=false` rows must be inventoried before any mapping or constraint change.

---

## 6. Android recipient comparison

```
| Field/behavior             | Web implementation                                     | Android implementation                                                           | Approved target (WP-01)                                                        | Gap/risk                                                                                                                                          |
| -------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Identity                   | Server UUID                                            | `serverId` (String) + local `rowId` + optional `localId`                         | Stable platform-native IDs; transfer identity separate                         | Different ID schemes expected; no shared runtime DB                                                                                               |
| Relationship               | Nullable `String`                                      | Not present on `RecipientEntity`                                                 | Nullable free-text on both (PO-01)                                             | Android schema/editor work still required                                                                                                         |
| Active / archive           | `boolean active`                                       | `active: Boolean` + `archivedAt: Long?`                                          | Canonical `ACTIVE`/`ARCHIVED`/`DELETED`; archive reversible, preserves history | Web has no timestamp for archive; Android has archive timestamp. Mapping of historical Web `active=false` is not proven to equal Android archived |
| Soft archive               | `deactivate()` → active=false                          | `setArchivedAt` / `setActive`                                                    | Archive reversible, read-only when archived                                    | Web lacks restore API                                                                                                                             |
| Permanent delete           | None                                                   | Explicit admin path with counts/safeguards (see RecipientAdmin / deletion tests) | Explicit legal/data operation, not automatic from archive                      | Web has no delete; Android does. Child retention behavior differs                                                                                 |
| Child retention on archive | Notes/plans retained (comment + FK RESTRICT)           | Notes can be deleted by recipient in some admin paths                            | History preserved on archive                                                   | Confirm Android permanent-delete safeguards before any cross-platform assumption                                                                  |
| Ownership checks           | Every service method scopes by authenticated caregiver | Local Room + server ownership on sync                                            | Caregiver-scoped                                                               | Consistent intent                                                                                                                                 |
```

**Data-loss risks identified:**

- Treating Web `active=false` as “deleted” would lose history (INFERENCE — do not do this).
- Introducing a hard-delete without inventory of dependents risks orphaning or cascade errors (FK currently RESTRICT).
- Android permanent deletion of a recipient can remove notes; Web never does. Any future shared semantics must not retroactively delete Web history.

---

## 7. Observation and verification field inventory

**Web `DailyNote` / `NoteAddendum` columns (nullable unless noted):**

```
| Field                           | Java type | DB type     | Nullability | Notes                                 |
| ------------------------------- | --------- | ----------- | ----------- | ------------------------------------- |
| mood, appetite, sleep, mobility | String    | VARCHAR(40) | nullable    | free-text style values                |
| medicationStatus                | String    | VARCHAR(20) | nullable    | CHECK IN ('TAKEN','MISSED','UNKNOWN') |
| medicationVerified              | Boolean   | BOOLEAN     | nullable    | separate verification flag            |
| painScore                       | Integer   | INTEGER     | nullable    | CHECK 0–10                            |
| fallReported                    | Boolean   | BOOLEAN     | nullable    |                                       |
| freeText                        | String    | TEXT        | nullable    |                                       |
```

**Create path:** `NoteRequest` accepts the same nullable types; service passes them through to the entity constructor. No defaulting of null → false / 0 observed in the inspected service code (CONFIRMED IN SOURCE).

**Android `NoteEntity` (cache):**

- mood, appetite, sleep, mobility, medicationTaken, text → non-null `String`
- pain → non-null `Int`
- fall → non-null `Boolean`

Representation gap: Android cache cannot currently express “not recorded / null / unknown” for several fields that Web can store as null.

**Explicit PO-05 states:** Not present as first-class columns or enums on either platform. Verification is partially present on Web (`medicationVerified`) but not generalized.

**Dangerous legacy interpretations searched for:**

- null → false / 0 conversion on write: not found in core create path.
- Default UI values that could be written as confirmed negatives: requires frontend form inspection (partial; not fully traced in this preflight).
- Inference of verification from presence of a value: medicationVerified is independent; other fields lack separate verification.

**Recommendation (preservation policy):** Until an explicit state model is implemented, treat existing nulls as `LEGACY_AMBIGUOUS` or preserve raw null; never rewrite null to a clinical negative. Any backfill that invents “false” or “0” for historical nulls is prohibited without clinical review.

---

## 8. Legacy-data ambiguity assessment

```
| Category                           | Evidence                                                        | Assessment                                                                           | Label                                                |
| ---------------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| Web `active=false` meaning         | Soft-deactivate only; no archive timestamp; no delete path      | Most likely “archived / inactive”; cannot prove absence of other historical meanings | BLOCKED — OWNER DECISION REQUIRED for mapping policy |
| Null observation values            | Schema and entity preserve null; no conversion found            | Ambiguous between “not recorded”, “unknown”, “never asked”, etc.                     | Preserve as-is or tag LEGACY_AMBIGUOUS               |
| Android non-null cache vs Web null | Different representations                                       | Cross-platform display of historical nulls will require a common state model         | Implementation work required later                   |
| Pre-V5 data                        | Table created with unique constraint and nullable columns in V5 | No evidence of earlier schema without uniqueness                                     | NOT VERIFIED for any pre-migration production data   |
| Addendum incomplete metadata       | Web addenda carry the same field set; Android text-only         | Structured clear / omit distinction not fully expressed                              | Preserve existing events                             |
```

---

## 9. Note identity and date/time behavior

**Identity:**

- PK: UUID
- Natural business key: `(recipient_id, note_date)` enforced by unique constraint since table creation
- Additional idempotency: `(caregiver_id, idempotency_key)` unique
- Service recovers concurrent create races by re-reading the winner row or throwing `NoteConflictException`

**Date handling:**

- Type: `java.time.LocalDate` / PostgreSQL `DATE`
- Column `note_date` non-updatable
- No Instant conversion of the calendar date itself in the entity or the inspected create path
- Caregiver-local calendar date is the intended basis (PO-04 / PO-03)

**Risks still requiring validation:**

- JSON serialization / React date-picker paths that might interpret a date-only value as UTC midnight and shift the day for some offsets
- Display formatting that could re-introduce timezone shifts
- Boundary cases (UTC±14, DST transitions) not yet evidenced by automated tests in this audit

**Boundary test scenarios to prepare (not executed here):**

1. Create note with LocalDate = 2026-03-29 (DST transition regions)
2. Client in UTC-12 / UTC+14 submitting the same calendar date
3. Round-trip: save date-only → load → display → save again without shift
4. Midnight local vs UTC midnight edge

---

## 10. Duplicate preflight results

**Schema:** `UNIQUE (recipient_id, note_date)` present from V5 (CONFIRMED IN SOURCE).

**Service:** Create path detects existing recipient+date and throws conflict; idempotency key provides additional protection (CONFIRMED IN SOURCE).

**Available data analysis:**

- Production / staging dataset: **not available** → production duplicate counts remain **NOT VERIFIED**.
- Test fixtures (`SyntheticNotes`, controller tests): no intentional duplicate (recipient, date) pairs observed; uniqueness is assumed by schema.
- Conclusion: Under the current schema, new duplicates cannot be inserted. Any historical duplicates that pre-dated the constraint (if the database was ever used before V5) are unknown.

**Disposition recommendation (do not execute):**

1. Inventory on the authoritative dataset (production or a faithful restore).
2. For each collision group report: count, content differences, presence of addenda, timestamps.
3. Product-owner decision on disposition (retain both under a new model, mark one as superseded, manual review, etc.).
4. Only after approval may a uniqueness constraint be relied upon for future writes; do not add additional constraints or merge/delete rows without that decision.

**Data source for this section:** Schema + source + test code only. No production claim is made.

---

## 11. Addenda / correction-event assessment

**Web:**

- `NoteAddendum` mirrors the observation fields of `DailyNote`.
- Original note fields are `updatable = false` (immutable original).
- Addenda are append-only (new rows); ordered by `created_at`.
- No explicit field-mask, `SET`/`CLEAR`/`UNKNOWN`/`NOT_RECORDED` operators.
- Omitted fields in an addendum are null; there is no way to distinguish “do not change” from “explicitly clear to null” in the current model.
- Ownership: caregiver_id on every addendum; service scopes by owner.

**Android:**

- `AddendumEntity` is text-only (`id`, `noteId`, `createdAt`, `text`).
- Structured corrections not present.

**PO-06 target:** Immutable typed events supporting both text addenda and structured corrections; explicit field mask; intentional clear distinct from omitted; original preserved.

**Examples to support later design (not implemented):**

1. Text-only addendum — free_text only.
2. Correction changing an existing value — structured event with SET on one field.
3. Explicit clear — CLEAR operator on a field (currently indistinguishable from null).
4. Omitted field — no entry in the event (must not clear).
5. Multiple corrections to same field — ordered by created_at; effective view is latest non-omitted.
6. Corrections at different times — timeline reconstruction.
7. Legacy incomplete metadata — preserve raw row; do not invent operators.

**Preservation rule:** Existing addendum rows must remain readable as historical events; do not rewrite them into a new schema without a reversible migration and provenance.

---

## 12. API and authorization assessment

**Recipient endpoints (representative):**

- Create / list / get / update / deactivate — all caregiver-scoped via `AuthContext.currentCaregiverId()` and owner queries.
- Inactive update blocked; inactive note create blocked.
- Cross-caregiver access returns not-found style responses (ownership queries).

**Note endpoints:**

- Create with optional idempotency key; conflict on duplicate (recipient, date) or idempotency reuse with different payload.
- Detail / history retrieval owner-scoped.
- Addendum creation similarly scoped and idempotent.

**DTO / nullability:**

- `NoteRequest` / entity / DB nullability largely align for the observation fields.
- TypeScript frontend types were not exhaustively audited in this pass; a later pass should confirm no “required” TypeScript fields that the backend treats as optional (or vice-versa).

**Compatibility note:** Introducing explicit observation-state enums or structured correction events will be additive if carefully versioned; changing existing null semantics or making previously optional fields required would be a breaking change.

---

## 13. Database and Flyway inventory

```
| Migration | Purpose                                                                                      |
| --------- | -------------------------------------------------------------------------------------------- |
| V1        | caregivers                                                                                   |
| V2        | care_recipients (relationship nullable, active boolean)                                      |
| V3        | care_plan_versions                                                                           |
| V4        | audit_events                                                                                 |
| V5        | daily_notes + note_addenda (unique recipient+date, nullable observations, CHECK constraints) |
| V6        | plan_source_type                                                                             |
```

**Current constraints of interest:**

- `uk_daily_note_recipient_date`
- `uk_daily_note_owner_idempotency`
- `uk_note_addenda_owner_idempotency`
- FK ON DELETE RESTRICT on caregiver and recipient
- pain_score 0–10, medication_status enum-like CHECK

**Ordering / conventions:** Sequential Vn__*.sql; additive style observed.

**Test database:** Spring test configuration present; synthetic fixtures used in unit/integration tests. No production connection used.

---

## 14. Migration readiness and proposed sequence

**Status of proposed migrations: DESIGN ONLY — NOT CREATED OR EXECUTED.**

### Proposed sequence (high-level)

1. **Read-only inventory scripts** (run against authorized non-production or production-with-approval copy)
- Count recipients by active flag
- Count notes, distinct (recipient_id, note_date), any residual collisions
- Count null vs non-null observation columns
- Count addenda per note
- **Purpose:** evidence for owner decisions
- **Destructive:** no
- **Gate:** product-owner review of counts

2. **Additive lifecycle columns** (only after mapping policy approved)
- e.g. `lifecycle_status` or `archived_at` + keep `active` for compatibility
- Backfill from existing `active` under approved rule
- **Destructive:** no (additive + reversible backfill)
- **Gate:** mapping policy signed off; recovery plan reviewed

3. **Observation-state representation** (additive)
- New columns or side table for explicit state + verification
- Preserve raw legacy values; mark LEGACY_AMBIGUOUS where needed
- **Destructive:** no
- **Gate:** clinical/product review of state model

4. **Correction-event model** (new tables)
- Immutable events with field mask / operators
- Dual-write or shadow period if required
- **Destructive:** no for existing data
- **Gate:** detailed design review

5. **Uniqueness / constraint hardening**
- Only after duplicate disposition complete
- **Blocked until** inventory + disposition approved

**Failure / recovery principles:**

- Prefer additive, dual-write, and reversible backfills.
- Never rely on “rollback the migration” to undo semantic reinterpretation of clinical data.
- Every migration that touches existing rows must produce a before/after diagnostic report.

Any migration depending on unresolved duplicates or ambiguous lifecycle/observation mapping is marked **BLOCKED**.

---

## 15. Synthetic fixture catalogue

All fixtures are synthetic; no real caregiver or patient data.

**Recipient fixtures**

```
| ID            | Description                              | Intended verification                                              |
| ------------- | ---------------------------------------- | ------------------------------------------------------------------ |
| R-ACTIVE      | active=true, relationship present        | Normal create/update/list                                          |
| R-INACTIVE    | active=false, historical notes exist     | Deactivate preserves children; update blocked; note create blocked |
| R-AMBIGUOUS   | active=false with no archive timestamp   | Mapping policy exercise                                            |
| R-MULTI-CHILD | active, multiple notes + addenda + plans | FK / cascade safety                                                |
```

**Observation fixtures**

```
| ID               | Description                                           | Intended verification                   |
| ---------------- | ----------------------------------------------------- | --------------------------------------- |
| O-TRUE / O-FALSE | explicit Boolean true/false                           | Recorded positive/negative              |
| O-ZERO           | painScore = 0                                         | Explicit zero ≠ null                    |
| O-NULL           | all observation fields null                           | Preserve null; do not treat as negative |
| O-MISSING        | field omitted from request                            | Same as null on create                  |
| O-UNKNOWN-MED    | medicationStatus = UNKNOWN                            | Existing CHECK value                    |
| O-LEGACY         | null + no state column                                | LEGACY_AMBIGUOUS classification         |
| O-VERIFIED       | medicationVerified true/false independently of status | Separate verification                   |
```

**Note / date fixtures**

```
| ID                  | Description                       | Intended verification           |
| ------------------- | --------------------------------- | ------------------------------- |
| N-SINGLE            | one note per (recipient, date)    | Happy path                      |
| N-COLLISION-ATTEMPT | second create same recipient+date | Conflict / idempotency recovery |
| N-TZ-BOUNDARY       | dates around midnight / DST       | Calendar-date stability         |
| N-HISTORICAL        | note_date in the past             | PO-03 preservation              |
```

**Addendum fixtures**

```
| ID            | Description                               | Intended verification            |
| ------------- | ----------------------------------------- | -------------------------------- |
| A-TEXT        | free_text only                            | Text addendum                    |
| A-STRUCTURED  | subset of fields non-null                 | Current overlay semantics        |
| A-NULL-FIELDS | all fields null except free_text or empty | Cannot distinguish clear vs omit |
| A-MULTI       | several addenda ordered by created_at     | Timeline reconstruction          |
| A-LEGACY      | incomplete metadata                       | Preserve raw                     |
```

These fixtures are for future automated tests; they are not injected into production resources by this preflight.

---

## 16. Automated test coverage inventory

**Inspected (not re-executed as a full suite in this audit):**

- `RecipientControllerTest`, recipient service ownership tests
- `NoteControllerTest`, `NoteIdempotencyTest`, `NoteRequestValidationTest`
- `NoteSignalServiceTest`, contract tests
- Android: `RecipientDaoTest`, `NoteDaoTest`, `AddendumPersistenceTest`, deletion/admin tests

**Gaps relative to WP-02 needs:**

- No production-scale duplicate inventory tests (expected — data-dependent)
- Limited explicit timezone / LocalDate round-trip tests
- No tests for explicit observation-state enums (feature not present)
- No tests for structured CLEAR vs OMIT correction semantics

Future WP-02 implementation work must expand fixtures and regression tests before any schema change that touches existing rows.

---

## 17. Manual verification scenarios

1. Create recipient → deactivate → attempt update and note create (expect blocked).
2. Create note with all observation fields null → retrieve → confirm nulls preserved.
3. Create note with painScore=0 and fallReported=false → confirm distinct from null.
4. Concurrent create with same idempotency key (identical vs differing payload).
5. Attempt second note same recipient+date (expect conflict).
6. Add multiple addenda → reconstruct effective view ordered by created_at.
7. Date pickers in multiple client timezones for the same calendar day.
8. List recipients including inactive; confirm historical notes still reachable by ID/date filters.

---

## 18. Risks and severity

```
| Risk                                                               | Severity            | Mitigation direction                                                           |
| ------------------------------------------------------------------ | ------------------- | ------------------------------------------------------------------------------ |
| Production contains (recipient, date) collisions unknown to schema | High                | Inventory before any constraint reliance or merge                              |
| Mapping active=false → ARCHIVED without review                     | Medium–High         | Owner decision + diagnostic counts                                             |
| Null observations rewritten to false/0                             | Critical (clinical) | Preserve raw; LEGACY_AMBIGUOUS policy                                          |
| Timezone shift of calendar dates                                   | High                | Explicit LocalDate contract + boundary tests                                   |
| Loss of original note content via future “edit”                    | High                | Keep original immutable; corrections as events                                 |
| Android permanent delete vs Web soft-only                          | Medium              | Document platform difference; never cascade Web deletes from Android semantics |
| Incomplete addendum field-mask                                     | Medium              | Design structured events before claiming PO-06 complete                        |
```

---

## 19. Unresolved product-owner questions

1. Exact mapping of every existing Web `active=false` row: treat all as ARCHIVED, or is a clinical/legal review of a sample required?
2. Disposition policy for any (recipient_id, note_date) collisions discovered in production (retain both, designate primary, manual merge with provenance, etc.)?
3. Preservation rule for historical null observations: leave as null, or add an explicit LEGACY_AMBIGUOUS marker column?
4. Is a restore/reactivate API required in the same release as the lifecycle model, or can it follow?
5. For structured corrections, is the first release required to support CLEAR / UNKNOWN / NOT_RECORDED operators, or is text-addendum + limited SET sufficient initially?

---

## 20. Blockers and required approvals

```
| Gate                                                | Status         | Required action                                    |
| --------------------------------------------------- | -------------- | -------------------------------------------------- |
| Duplicate inventory on relevant dataset             | Incomplete     | Engineering + data owner: run read-only counts     |
| Duplicate disposition approved                      | Open           | Product owner                                      |
| Legacy lifecycle mapping policy                     | Open           | Product owner (+ clinical if sample review needed) |
| Ambiguous observation preservation policy           | Open           | Product owner / clinical reviewer                  |
| Date-only / timezone validation                     | Partial        | Engineering: automated boundary tests              |
| Addendum/correction behavior sufficiently specified | Partial        | Design review of event model                       |
| Migration plan + recovery reviewed                  | Draft only     | Engineering + product review                       |
| Representative fixtures + regression tests prepared | Catalogue only | Engineering before any schema change               |
```

---

## 21. WP-02 implementation recommendations

1. Keep all existing columns and nullability until an additive state model is ready.
2. Prefer new columns / tables over in-place reinterpretation of null or active flags.
3. Implement observation states and correction events as additive features with dual-read where needed.
4. Do not add further uniqueness constraints or drop nullability until inventories and policies are closed.
5. Expand synthetic fixtures and timezone tests before the first migration PR.
6. Treat Android text-only addenda and non-null note fields as a separate representation problem that will be solved by the common semantic contract, not by forcing Android data into Web’s current nullable columns (or vice-versa).

---

## 22. Exit-readiness assessment

**Outcome: NOT READY — DATA OR DECISION BLOCKERS REMAIN**

**What can proceed safely now:**

- Continued design of the observation-state and correction-event models.
- Preparation of expanded automated tests and synthetic fixtures.
- Read-only inventory scripts (against non-production or authorized copies).
- Documentation and owner-decision workshops on the five open questions above.

**What remains prohibited:**

- Any schema change, constraint addition, backfill, merge, or deletion of caregiver records.
- Any reinterpretation of historical null or inactive values as clinical positives/negatives.
- Claiming production is free of (recipient, date) duplicates.
- Starting WP-03 or WP-04 implementation work that depends on closed WP-02 data contracts.

**Evidence still required for READY status:**

1. Duplicate inventory results (or explicit written waiver that the current unique constraint has always been present and no pre-V5 data exists).
2. Signed mapping policy for inactive recipients.
3. Signed preservation policy for null/legacy observations.
4. Reviewed migration plan with recovery strategy and representative fixtures.
5. Passing boundary tests for LocalDate calendar-date integrity.

Until those gates close, WP-02 remains in preflight.

---

## Decision and blocker register (summary)

```
| Item                       | Evidence found                                    | Decision/status             | Required action            | Blocks                       |
| -------------------------- | ------------------------------------------------- | --------------------------- | -------------------------- | ---------------------------- |
| PO-01 relationship         | Web already has nullable relationship             | APPROVED (satisfied on Web) | Android schema later       | None for Web                 |
| PO-02 lifecycle mapping    | active boolean only; no archive timestamp         | Mapping policy open         | Owner decision + inventory | Lifecycle migration          |
| PO-04 duplicates           | Unique constraint since V5; prod counts unknown   | Inventory incomplete        | Read-only inventory        | Constraint reliance / merges |
| PO-05 legacy observations  | Null preserved; no null→false found               | Preservation policy open    | Owner/clinical policy      | State-model backfill         |
| PO-06 corrections          | Structured overlays exist; no explicit CLEAR/OMIT | Design needed               | Event-model design review  | Full correction semantics    |
| Date/timezone integrity    | LocalDate used; boundary tests incomplete         | Validation partial          | Automated boundary tests   | Date-related migrations      |
| Stable identity / children | UUID + RESTRICT FKs; soft-deactivate              | Largely safe                | Confirm no cascade deletes | Delete paths                 |
| API compatibility          | Owner-scoped; nullability aligned                 | Additive changes preferred  | Versioned DTO design       | Breaking API changes         |
| Migration safety           | Additive preferred; no irreversible plan yet      | Plan draft only             | Full review gate           | Any Flyway change            |
```

---

*End of WP-02 preflight report. No application source, database schema, migration, or production data was modified. No commit, push, or pull request was created.*