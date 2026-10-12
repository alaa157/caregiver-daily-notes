CREATE TABLE care_plan_versions (
  id UUID PRIMARY KEY,
  recipient_id UUID NOT NULL,
  version_number INTEGER NOT NULL CHECK (version_number > 0),
  status VARCHAR(32) NOT NULL CHECK (status IN
    ('SUGGESTED', 'ACCEPTED', 'EDITED_AND_ACCEPTED', 'DISMISSED', 'ARCHIVED')),
  content TEXT NOT NULL,
  source_summary_id VARCHAR(160),
  source_plan_id UUID,
  evidence_note_ids VARCHAR(4000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_plan_recipient FOREIGN KEY (recipient_id)
    REFERENCES care_recipients(id) ON DELETE RESTRICT,
  CONSTRAINT fk_plan_source_version FOREIGN KEY (source_plan_id)
    REFERENCES care_plan_versions(id) ON DELETE RESTRICT,
  CONSTRAINT uk_plan_recipient_version UNIQUE (recipient_id, version_number)
);

CREATE INDEX idx_plan_recipient_version
  ON care_plan_versions (recipient_id, version_number DESC);
CREATE INDEX idx_plan_recipient_status
  ON care_plan_versions (recipient_id, status);

-- PostgreSQL enforces the core invariant even if application code is bypassed.
CREATE UNIQUE INDEX uk_one_active_accepted_plan_per_recipient
  ON care_plan_versions (recipient_id)
  WHERE status IN ('ACCEPTED', 'EDITED_AND_ACCEPTED');
