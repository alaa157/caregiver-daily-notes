# Medication intake correction (#840)

Intake events are append-only *records*, but a mis-tapped dose can be voided:

- `DELETE /api/recipients/{recipientId}/medications/{medicationId}/intake-events/{eventId}?reason=...`
  sets `voided_at`, `voided_by` (from the verified JWT) and `void_reason`.
  The row is never deleted and the original state/time are never rewritten.
- Voiding an already-voided event returns `409 INTAKE_ALREADY_VOIDED`;
  a foreign or missing event returns `404`; ownership comes from the token.
- `GET` history still returns voided events, now with `voided: true`,
  `voidedAt`, `voidedBy` and `voidReason`. Active events keep their original
  seven-field response shape (void fields are omitted when null).
- A `MEDICATION_INTAKE_VOIDED` audit event is written with identifiers only.

Migration `V17__add_medication_intake_event_void.sql` adds the three nullable
columns, a provenance check constraint and the `voided_by` foreign key.
