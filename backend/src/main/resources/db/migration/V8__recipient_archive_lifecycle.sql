-- Add reversible lifecycle metadata without deleting or rewriting any child data.
-- active=false is historically ambiguous, so it is explicitly retained for review.
ALTER TABLE care_recipients
  ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN archived_by UUID,
  ADD COLUMN restored_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN restored_by UUID;

UPDATE care_recipients
SET lifecycle_status = CASE WHEN active = TRUE THEN 'ACTIVE' ELSE 'LEGACY_INACTIVE_REVIEW' END;

ALTER TABLE care_recipients
  ADD CONSTRAINT chk_care_recipients_lifecycle_status
    CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED', 'LEGACY_INACTIVE_REVIEW')),
  ADD CONSTRAINT chk_care_recipients_lifecycle_active
    CHECK ((lifecycle_status = 'ACTIVE' AND active = TRUE)
      OR (lifecycle_status IN ('ARCHIVED', 'LEGACY_INACTIVE_REVIEW') AND active = FALSE)),
  ADD CONSTRAINT chk_care_recipients_archive_actor_time
    CHECK ((archived_at IS NULL AND archived_by IS NULL)
      OR (archived_at IS NOT NULL AND archived_by IS NOT NULL)),
  ADD CONSTRAINT chk_care_recipients_restore_actor_time
    CHECK ((restored_at IS NULL AND restored_by IS NULL)
      OR (restored_at IS NOT NULL AND restored_by IS NOT NULL)),
  ADD CONSTRAINT chk_care_recipients_archived_has_provenance
    CHECK (lifecycle_status <> 'ARCHIVED' OR (archived_at IS NOT NULL AND archived_by IS NOT NULL)),
  ADD CONSTRAINT chk_care_recipients_legacy_has_no_archive_claim
    CHECK (lifecycle_status <> 'LEGACY_INACTIVE_REVIEW'
      OR (archived_at IS NULL AND archived_by IS NULL AND restored_at IS NULL AND restored_by IS NULL)),
  ADD CONSTRAINT fk_care_recipients_archived_by
    FOREIGN KEY (archived_by) REFERENCES caregivers(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_care_recipients_restored_by
    FOREIGN KEY (restored_by) REFERENCES caregivers(id) ON DELETE RESTRICT;

CREATE INDEX idx_care_recipients_lifecycle
  ON care_recipients (caregiver_id, lifecycle_status, created_at);
