CREATE TABLE recipient_medications (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL,
  display_name TEXT NOT NULL,
  dose_text TEXT NOT NULL,
  schedule_text TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_recipient_medications_caregiver
    FOREIGN KEY (caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_recipient_medications_recipient_owner
    FOREIGN KEY (recipient_id, caregiver_id)
    REFERENCES care_recipients (id, caregiver_id) ON DELETE RESTRICT
);

CREATE INDEX idx_recipient_medications_owner_recipient
  ON recipient_medications (caregiver_id, recipient_id);
