ALTER TABLE care_recipients
  ADD CONSTRAINT uk_care_recipients_id_caregiver UNIQUE (id, caregiver_id);

ALTER TABLE safety_acknowledgements
  ADD CONSTRAINT fk_safety_acknowledgements_recipient_owner
    FOREIGN KEY (recipient_id, caregiver_id)
    REFERENCES care_recipients (id, caregiver_id)
    ON DELETE RESTRICT;
