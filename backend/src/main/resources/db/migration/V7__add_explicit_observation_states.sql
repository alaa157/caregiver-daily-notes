ALTER TABLE daily_notes ADD COLUMN mood_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN mood_value VARCHAR(40);
ALTER TABLE daily_notes ADD COLUMN appetite_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN appetite_value VARCHAR(40);
ALTER TABLE daily_notes ADD COLUMN sleep_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN sleep_value VARCHAR(40);
ALTER TABLE daily_notes ADD COLUMN mobility_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN mobility_value VARCHAR(40);
ALTER TABLE daily_notes ADD COLUMN medication_status_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN medication_status_value VARCHAR(20);
ALTER TABLE daily_notes ADD COLUMN medication_verification_state VARCHAR(20);
ALTER TABLE daily_notes ADD COLUMN medication_verification_provided BOOLEAN;
ALTER TABLE daily_notes ADD COLUMN pain_score_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN pain_score_value INTEGER;
ALTER TABLE daily_notes ADD COLUMN fall_reported_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN fall_reported_value BOOLEAN;
ALTER TABLE daily_notes ADD COLUMN free_text_state VARCHAR(32);
ALTER TABLE daily_notes ADD COLUMN free_text_value TEXT;

UPDATE daily_notes
SET mood_state = 'LEGACY_AMBIGUOUS',
    appetite_state = 'LEGACY_AMBIGUOUS',
    sleep_state = 'LEGACY_AMBIGUOUS',
    mobility_state = 'LEGACY_AMBIGUOUS',
    medication_status_state = CASE
      WHEN medication_status = 'UNKNOWN' THEN 'UNKNOWN'
      ELSE 'LEGACY_AMBIGUOUS'
    END,
    medication_verification_state = CASE
      WHEN medication_verified IS TRUE THEN 'VERIFIED'
      WHEN medication_verified IS FALSE THEN 'UNVERIFIED'
      ELSE 'NOT_ASSERTED'
    END,
    medication_verification_provided = medication_verified IS NOT NULL,
    pain_score_state = 'LEGACY_AMBIGUOUS',
    fall_reported_state = 'LEGACY_AMBIGUOUS',
    free_text_state = 'LEGACY_AMBIGUOUS';

ALTER TABLE daily_notes ALTER COLUMN mood_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN appetite_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN sleep_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN mobility_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN medication_status_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN medication_verification_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN medication_verification_provided SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN pain_score_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN fall_reported_state SET NOT NULL;
ALTER TABLE daily_notes ALTER COLUMN free_text_state SET NOT NULL;

ALTER TABLE note_addenda ADD COLUMN mood_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN mood_value VARCHAR(40);
ALTER TABLE note_addenda ADD COLUMN appetite_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN appetite_value VARCHAR(40);
ALTER TABLE note_addenda ADD COLUMN sleep_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN sleep_value VARCHAR(40);
ALTER TABLE note_addenda ADD COLUMN mobility_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN mobility_value VARCHAR(40);
ALTER TABLE note_addenda ADD COLUMN medication_status_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN medication_status_value VARCHAR(20);
ALTER TABLE note_addenda ADD COLUMN medication_verification_state VARCHAR(20);
ALTER TABLE note_addenda ADD COLUMN medication_verification_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN pain_score_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN pain_score_value INTEGER;
ALTER TABLE note_addenda ADD COLUMN fall_reported_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN fall_reported_value BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN free_text_state VARCHAR(32);
ALTER TABLE note_addenda ADD COLUMN free_text_value TEXT;
ALTER TABLE note_addenda ADD COLUMN mood_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN appetite_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN sleep_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN mobility_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN medication_status_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN pain_score_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN fall_reported_provided BOOLEAN;
ALTER TABLE note_addenda ADD COLUMN free_text_provided BOOLEAN;

UPDATE note_addenda
SET mood_state = CASE WHEN mood IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    appetite_state = CASE WHEN appetite IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    sleep_state = CASE WHEN sleep IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    mobility_state = CASE WHEN mobility IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    medication_status_state = CASE
      WHEN medication_status IS NULL THEN 'NOT_RECORDED'
      WHEN medication_status = 'UNKNOWN' THEN 'UNKNOWN'
      ELSE 'LEGACY_AMBIGUOUS'
    END,
    medication_verification_state = CASE
      WHEN medication_verified IS TRUE THEN 'VERIFIED'
      WHEN medication_verified IS FALSE THEN 'UNVERIFIED'
      ELSE 'NOT_ASSERTED'
    END,
    medication_verification_provided = medication_verified IS NOT NULL,
    pain_score_state = CASE WHEN pain_score IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    fall_reported_state = CASE WHEN fall_reported IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    free_text_state = CASE WHEN free_text IS NULL THEN 'NOT_RECORDED' ELSE 'LEGACY_AMBIGUOUS' END,
    mood_provided = mood IS NOT NULL,
    appetite_provided = appetite IS NOT NULL,
    sleep_provided = sleep IS NOT NULL,
    mobility_provided = mobility IS NOT NULL,
    medication_status_provided = medication_status IS NOT NULL,
    pain_score_provided = pain_score IS NOT NULL,
    fall_reported_provided = fall_reported IS NOT NULL,
    free_text_provided = free_text IS NOT NULL;

ALTER TABLE note_addenda ALTER COLUMN mood_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN appetite_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN sleep_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN mobility_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN medication_status_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN medication_verification_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN medication_verification_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN pain_score_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN fall_reported_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN free_text_state SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN mood_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN appetite_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN sleep_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN mobility_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN medication_status_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN pain_score_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN fall_reported_provided SET NOT NULL;
ALTER TABLE note_addenda ALTER COLUMN free_text_provided SET NOT NULL;

ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_mood_observation
  CHECK (mood_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((mood_state = 'RECORDED' AND mood_value IS NOT NULL)
      OR (mood_state <> 'RECORDED' AND mood_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_appetite_observation
  CHECK (appetite_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((appetite_state = 'RECORDED' AND appetite_value IS NOT NULL)
      OR (appetite_state <> 'RECORDED' AND appetite_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_sleep_observation
  CHECK (sleep_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((sleep_state = 'RECORDED' AND sleep_value IS NOT NULL)
      OR (sleep_state <> 'RECORDED' AND sleep_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_mobility_observation
  CHECK (mobility_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((mobility_state = 'RECORDED' AND mobility_value IS NOT NULL)
      OR (mobility_state <> 'RECORDED' AND mobility_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_medication_observation
  CHECK (medication_status_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((medication_status_state = 'RECORDED' AND medication_status_value IS NOT NULL
        AND medication_status_value IN ('TAKEN', 'MISSED'))
      OR (medication_status_state <> 'RECORDED' AND medication_status_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_pain_observation
  CHECK (pain_score_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((pain_score_state = 'RECORDED' AND pain_score_value IS NOT NULL
        AND pain_score_value BETWEEN 0 AND 10)
      OR (pain_score_state <> 'RECORDED' AND pain_score_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_fall_observation
  CHECK (fall_reported_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((fall_reported_state = 'RECORDED' AND fall_reported_value IS NOT NULL)
      OR (fall_reported_state <> 'RECORDED' AND fall_reported_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_text_observation
  CHECK (free_text_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((free_text_state = 'RECORDED' AND free_text_value IS NOT NULL)
      OR (free_text_state <> 'RECORDED' AND free_text_value IS NULL)));
ALTER TABLE daily_notes ADD CONSTRAINT chk_daily_note_verification
  CHECK (medication_verification_state IN ('VERIFIED', 'UNVERIFIED', 'NOT_ASSERTED'));

ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_mood_observation
  CHECK (mood_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((mood_state = 'RECORDED' AND mood_value IS NOT NULL)
      OR (mood_state <> 'RECORDED' AND mood_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_appetite_observation
  CHECK (appetite_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((appetite_state = 'RECORDED' AND appetite_value IS NOT NULL)
      OR (appetite_state <> 'RECORDED' AND appetite_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_sleep_observation
  CHECK (sleep_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((sleep_state = 'RECORDED' AND sleep_value IS NOT NULL)
      OR (sleep_state <> 'RECORDED' AND sleep_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_mobility_observation
  CHECK (mobility_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((mobility_state = 'RECORDED' AND mobility_value IS NOT NULL)
      OR (mobility_state <> 'RECORDED' AND mobility_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_medication_observation
  CHECK (medication_status_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((medication_status_state = 'RECORDED' AND medication_status_value IS NOT NULL
        AND medication_status_value IN ('TAKEN', 'MISSED'))
      OR (medication_status_state <> 'RECORDED' AND medication_status_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_pain_observation
  CHECK (pain_score_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((pain_score_state = 'RECORDED' AND pain_score_value IS NOT NULL
        AND pain_score_value BETWEEN 0 AND 10)
      OR (pain_score_state <> 'RECORDED' AND pain_score_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_fall_observation
  CHECK (fall_reported_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((fall_reported_state = 'RECORDED' AND fall_reported_value IS NOT NULL)
      OR (fall_reported_state <> 'RECORDED' AND fall_reported_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_text_observation
  CHECK (free_text_state IN ('RECORDED', 'UNKNOWN', 'NOT_RECORDED', 'NOT_APPLICABLE', 'LEGACY_AMBIGUOUS')
    AND ((free_text_state = 'RECORDED' AND free_text_value IS NOT NULL)
      OR (free_text_state <> 'RECORDED' AND free_text_value IS NULL)));
ALTER TABLE note_addenda ADD CONSTRAINT chk_note_addendum_verification
  CHECK (medication_verification_state IN ('VERIFIED', 'UNVERIFIED', 'NOT_ASSERTED'));
