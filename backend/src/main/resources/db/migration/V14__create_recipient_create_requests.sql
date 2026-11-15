CREATE TABLE recipient_create_requests (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  request_digest VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recipient_create_requests_caregiver
    FOREIGN KEY (caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_recipient_create_requests_recipient_owner
    FOREIGN KEY (recipient_id, caregiver_id)
    REFERENCES care_recipients (id, caregiver_id) ON DELETE RESTRICT,
  CONSTRAINT uk_recipient_create_requests_owner_key
    UNIQUE (caregiver_id, idempotency_key)
);

CREATE INDEX idx_recipient_create_requests_recipient
  ON recipient_create_requests (recipient_id);
