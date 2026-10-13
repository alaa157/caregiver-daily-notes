CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  actor_caregiver_id UUID,
  event_type VARCHAR(80) NOT NULL,
  target_type VARCHAR(80),
  target_id VARCHAR(160),
  safe_metadata VARCHAR(1000),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_caregiver_id)
    REFERENCES caregivers(id) ON DELETE RESTRICT
);

CREATE INDEX idx_audit_actor_time ON audit_events (actor_caregiver_id, occurred_at);
CREATE INDEX idx_audit_target ON audit_events (target_type, target_id);
CREATE INDEX idx_audit_time ON audit_events (occurred_at);
