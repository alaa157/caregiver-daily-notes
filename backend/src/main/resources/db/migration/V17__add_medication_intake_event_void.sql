-- Voiding is a correction, never a deletion: the original event stays in the
-- audit trail with provenance. All three columns are nullable so existing rows
-- are untouched, and a void claim always carries both actor and timestamp.
ALTER TABLE medication_intake_events
  ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN voided_by UUID,
  ADD COLUMN void_reason VARCHAR(500);

ALTER TABLE medication_intake_events
  ADD CONSTRAINT fk_medication_intake_events_voided_by
    FOREIGN KEY (voided_by) REFERENCES caregivers(id) ON DELETE RESTRICT;

ALTER TABLE medication_intake_events
  ADD CONSTRAINT chk_medication_intake_events_void_provenance
    CHECK ((voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (voided_at IS NOT NULL AND voided_by IS NOT NULL));
