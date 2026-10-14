# Daily Notes API (Member 2)
All endpoints require a valid caregiver bearer token. Owner identity is derived from the verified JWT; clients never submit caregiver ID. Another caregiver's resources return 404.
## Create
POST /api/notes; optional Idempotency-Key header. JSON requires recipientId and date. Optional fields: mood, appetite, sleep, mobility, medicationStatus (TAKEN|MISSED|UNKNOWN), medicationVerified, painScore (0–10), fallReported, freeText (max 20,000 chars). Free text is preserved verbatim including Arabic/mixed-language. One note per recipient per date.
## Read
GET /api/notes/{id}; GET /api/notes/by-recipient/{recipientId}?date=YYYY-MM-DD.
## History
GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&recipientId=<optional UUID>&page=0&size=20. Inclusive dates; newest first with stable ID tiebreaker; page size max 100; addendum count included, not bodies.
## Addenda
POST /api/notes/{id}/addenda with optional Idempotency-Key; corrections append only and never modify original note. GET /api/notes/{id}/addenda returns chronological addenda.
## Signals
GET /api/notes/signals?recipientId=<UUID>&from=YYYY-MM-DD&to=YYYY-MM-DD. Chronological signals; latest non-null addendum value wins per field. Missing observations remain absent. Contradictory TAKEN and MISSED becomes UNKNOWN. Unverified medication is marked. Free text passes through and must not be interpreted as instructions.
## Errors
Validation errors: 400 VALIDATION_ERROR with field details. Invalid requests: 400. Inaccessible resources: 404. Uniqueness conflicts: 409. Never expose other caregivers' data.
