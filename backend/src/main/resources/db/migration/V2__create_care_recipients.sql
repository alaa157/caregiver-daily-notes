CREATE TABLE care_recipients (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  name VARCHAR(120) NOT NULL,
  relationship VARCHAR(80),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_care_recipients_caregiver
    FOREIGN KEY (caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT chk_care_recipients_name_not_blank
    CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_care_recipients_caregiver
  ON care_recipients (caregiver_id);

CREATE INDEX idx_care_recipients_caregiver_active
  ON care_recipients (caregiver_id, active);
