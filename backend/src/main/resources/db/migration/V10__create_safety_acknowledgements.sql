CREATE TABLE safety_acknowledgements (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL,
  rule_id VARCHAR(80) NOT NULL,
  period_end_inclusive DATE NOT NULL,
  period_days INTEGER NOT NULL,
  acknowledged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_safety_acknowledgements_caregiver
    FOREIGN KEY (caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_safety_acknowledgements_recipient
    FOREIGN KEY (recipient_id) REFERENCES care_recipients(id) ON DELETE RESTRICT,
  CONSTRAINT chk_safety_acknowledgements_period_days CHECK (period_days > 0),
  CONSTRAINT uk_safety_acknowledgements_period
    UNIQUE (caregiver_id, recipient_id, rule_id, period_end_inclusive, period_days)
);

CREATE INDEX idx_safety_ack_owner_recipient_period
  ON safety_acknowledgements (caregiver_id, recipient_id, period_end_inclusive, period_days);
