CREATE TABLE note_correction_batches (
  id UUID PRIMARY KEY,
  note_id UUID NOT NULL,
  caregiver_id UUID NOT NULL,
  idempotency_key VARCHAR(100),
  request_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_correction_batches_note FOREIGN KEY(note_id) REFERENCES daily_notes(id) ON DELETE RESTRICT,
  CONSTRAINT fk_note_correction_batches_caregiver FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT uk_note_correction_batch_owner_key UNIQUE(caregiver_id, idempotency_key)
);

CREATE INDEX idx_note_correction_batches_note ON note_correction_batches(note_id, created_at, id);

CREATE TABLE note_correction_events (
  id UUID PRIMARY KEY,
  batch_id UUID NOT NULL,
  note_id UUID NOT NULL,
  caregiver_id UUID NOT NULL,
  sequence_no BIGINT NOT NULL,
  field_key VARCHAR(32) NOT NULL,
  operation VARCHAR(20) NOT NULL,
  value_json TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_correction_events_batch FOREIGN KEY(batch_id) REFERENCES note_correction_batches(id) ON DELETE RESTRICT,
  CONSTRAINT fk_note_correction_events_note FOREIGN KEY(note_id) REFERENCES daily_notes(id) ON DELETE RESTRICT,
  CONSTRAINT fk_note_correction_events_caregiver FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
  CONSTRAINT chk_note_correction_field CHECK(field_key IN ('mood','appetite','sleep','mobility','medicationStatus','painScore','fallReported','freeText')),
  CONSTRAINT chk_note_correction_operation CHECK(operation IN ('SET','CLEAR','UNKNOWN','NOT_RECORDED')),
  CONSTRAINT chk_note_correction_value CHECK((operation = 'SET' AND value_json IS NOT NULL) OR (operation <> 'SET' AND value_json IS NULL)),
  CONSTRAINT uk_note_correction_event_sequence UNIQUE(note_id, sequence_no)
);

CREATE INDEX idx_note_correction_events_batch ON note_correction_events(batch_id, sequence_no);
CREATE INDEX idx_note_correction_events_owner_sequence ON note_correction_events(caregiver_id, note_id, sequence_no);
