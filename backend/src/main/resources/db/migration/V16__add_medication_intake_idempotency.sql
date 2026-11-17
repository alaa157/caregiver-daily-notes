-- Idempotency for intake writes. The key is nullable so existing rows are left
-- untouched; new writes always supply one through the Idempotency-Key header.
ALTER TABLE medication_intake_events
  ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE medication_intake_events
  ADD CONSTRAINT uk_medication_intake_events_owner_key
    UNIQUE (caregiver_id, idempotency_key);
