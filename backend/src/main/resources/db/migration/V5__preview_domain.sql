CREATE TABLE recipients (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  name VARCHAR(320) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_recipients_caregiver ON recipients(caregiver_id);

CREATE TABLE notes (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL REFERENCES recipients(id),
  note_date DATE NOT NULL,
  mood VARCHAR(32),
  appetite VARCHAR(32),
  sleep VARCHAR(32),
  mobility VARCHAR(32),
  medication_taken VARCHAR(32),
  pain INTEGER NOT NULL DEFAULT 0,
  fall BOOLEAN NOT NULL DEFAULT FALSE,
  note_text TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_notes_recipient_day UNIQUE (recipient_id, note_date)
);
CREATE INDEX ix_notes_caregiver_recipient ON notes(caregiver_id, recipient_id);

CREATE TABLE addenda (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  note_id UUID NOT NULL REFERENCES notes(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  addendum_text TEXT NOT NULL
);
CREATE INDEX ix_addenda_note ON addenda(note_id);

CREATE TABLE plans (
  id UUID PRIMARY KEY,
  caregiver_id UUID NOT NULL,
  recipient_id UUID NOT NULL REFERENCES recipients(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_plans_caregiver ON plans(caregiver_id);

CREATE TABLE plan_versions (
  id UUID PRIMARY KEY,
  plan_id UUID NOT NULL REFERENCES plans(id),
  version INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_plan_versions_plan_version UNIQUE (plan_id, version)
);

CREATE TABLE plan_version_items (
  version_id UUID NOT NULL REFERENCES plan_versions(id),
  item VARCHAR(500) NOT NULL
);
