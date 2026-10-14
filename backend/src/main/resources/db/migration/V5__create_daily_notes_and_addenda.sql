CREATE TABLE daily_notes (
 id UUID PRIMARY KEY, caregiver_id UUID NOT NULL, recipient_id UUID NOT NULL, note_date DATE NOT NULL,
 mood VARCHAR(40), appetite VARCHAR(40), sleep VARCHAR(40), mobility VARCHAR(40), medication_status VARCHAR(20),
 medication_verified BOOLEAN, pain_score INTEGER, fall_reported BOOLEAN, free_text TEXT, idempotency_key VARCHAR(100),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_daily_notes_caregiver FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
 CONSTRAINT fk_daily_notes_recipient FOREIGN KEY(recipient_id) REFERENCES care_recipients(id) ON DELETE RESTRICT,
 CONSTRAINT chk_daily_notes_pain_score CHECK(pain_score IS NULL OR pain_score BETWEEN 0 AND 10),
 CONSTRAINT chk_daily_notes_medication_status CHECK(medication_status IS NULL OR medication_status IN ('TAKEN','MISSED','UNKNOWN')),
 CONSTRAINT uk_daily_note_recipient_date UNIQUE(recipient_id,note_date),
 CONSTRAINT uk_daily_note_owner_idempotency UNIQUE(caregiver_id,idempotency_key));
 CREATE INDEX idx_daily_notes_owner_date ON daily_notes(caregiver_id,note_date DESC,id);
 CREATE INDEX idx_daily_notes_recipient_date ON daily_notes(recipient_id,note_date DESC);
 CREATE TABLE note_addenda (
 id UUID PRIMARY KEY,note_id UUID NOT NULL,caregiver_id UUID NOT NULL,mood VARCHAR(40),appetite VARCHAR(40),sleep VARCHAR(40),mobility VARCHAR(40),
 medication_status VARCHAR(20),medication_verified BOOLEAN,pain_score INTEGER,fall_reported BOOLEAN,free_text TEXT,idempotency_key VARCHAR(100),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_note_addenda_note FOREIGN KEY(note_id) REFERENCES daily_notes(id) ON DELETE RESTRICT,
 CONSTRAINT fk_note_addenda_caregiver FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE RESTRICT,
 CONSTRAINT chk_note_addenda_pain_score CHECK(pain_score IS NULL OR pain_score BETWEEN 0 AND 10),
 CONSTRAINT chk_note_addenda_medication_status CHECK(medication_status IS NULL OR medication_status IN ('TAKEN','MISSED','UNKNOWN')),
 CONSTRAINT uk_note_addenda_owner_idempotency UNIQUE(caregiver_id,idempotency_key));
 CREATE INDEX idx_note_addenda_note_created ON note_addenda(note_id,created_at,id);