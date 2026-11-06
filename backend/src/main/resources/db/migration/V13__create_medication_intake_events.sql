ALTER TABLE recipient_medications
  ADD CONSTRAINT uk_recipient_medications_id_caregiver_recipient
    UNIQUE (id, caregiver_id, recipient_id);

CREATE TABLE medication_intake_events (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL,
  medication_id UUID NOT NULL,
  intake_state VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  verification_state VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_medication_intake_events_caregiver
    FOREIGN KEY (caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_medication_intake_events_recipient_owner
    FOREIGN KEY (recipient_id, caregiver_id)
    REFERENCES care_recipients (id, caregiver_id) ON DELETE RESTRICT,
  CONSTRAINT fk_medication_intake_events_medication_owner
    FOREIGN KEY (medication_id, caregiver_id, recipient_id)
    REFERENCES recipient_medications (id, caregiver_id, recipient_id) ON DELETE RESTRICT,
  CONSTRAINT chk_medication_intake_events_intake_state
    CHECK (intake_state IN ('TAKEN', 'MISSED', 'UNSURE')),
  CONSTRAINT chk_medication_intake_events_verification_state
    CHECK (verification_state IN ('VERIFIED', 'UNVERIFIED', 'NOT_ASSERTED'))
);

CREATE INDEX idx_medication_intake_events_caregiver_recipient_medication_time
  ON medication_intake_events (caregiver_id, recipient_id, medication_id, occurred_at, id);
