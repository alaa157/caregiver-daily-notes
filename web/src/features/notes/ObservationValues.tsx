import { observationFields, type Observations } from '../../api/notes';
import {
  fromLegacyValue,
  type Observation,
  type ObservationState,
  type VerificationValue,
} from '../../api/observation';
import { useI18n } from '../../i18n/I18nProvider';
import { fieldLabels } from './noteForm';
import {
  observationStateLabel,
  verificationLabel,
} from './ObservationStateField';

/**
 * Display surface for observations.
 * Distinguishes RECORDED (incl. false / 0 / empty string), UNKNOWN,
 * NOT_RECORDED, NOT_APPLICABLE, and LEGACY_AMBIGUOUS without relying
 * on colour or blank space alone.
 *
 * Accepts the current flat Observations (legacy backend) and maps
 * null → LEGACY_AMBIGUOUS so historical data is never presented as a
 * deliberate "not recorded".
 */
export function ObservationValues({
  values,
  correction = false,
}: {
  values: Observations;
  correction?: boolean;
}) {
  const { t, language } = useI18n();

  function asObs(field: keyof Observations): Observation<unknown> {
    return fromLegacyValue(values[field]);
  }

  function renderTypedValue(field: keyof Observations, value: unknown): string {
    if (value === '') return t('notesEmptyValue');
    if (typeof value === 'boolean') return t(value ? 'notesYes' : 'notesNo');
    if (field === 'medicationStatus') {
      if (value === 'TAKEN') return t('notesTaken');
      if (value === 'MISSED') return t('notesMissed');
      if (value === 'UNKNOWN') return t('notesUnknown');
    }
    if (typeof value === 'number') {
      return new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
    }
    return String(value ?? '');
  }

  function renderObservation(field: keyof Observations) {
    const obs = asObs(field);
    const state = obs.state as ObservationState;

    if (state === 'RECORDED') {
      const typed = renderTypedValue(field, obs.value);
      return (
        <>
          <span className="obs-display-state" data-state="RECORDED">
            {t(observationStateLabel('RECORDED'))}
          </span>
          <span className="obs-display-value note-verbatim" dir="auto">
            {typed}
          </span>
        </>
      );
    }

    if (state === 'LEGACY_AMBIGUOUS') {
      const raw = obs.rawValue;
      return (
        <>
          <span className="obs-display-state obs-display-state--legacy" data-state="LEGACY_AMBIGUOUS">
            {t(observationStateLabel('LEGACY_AMBIGUOUS'))}
          </span>
          {raw !== null && raw !== undefined && (
            <span className="obs-display-raw note-verbatim" dir="auto" title={t('obsLegacyRawHint')}>
              {renderTypedValue(field, raw)}
            </span>
          )}
          <span className="obs-display-note">{t('obsStateLegacyAmbiguousHint')}</span>
        </>
      );
    }

    return (
      <span className="obs-display-state" data-state={state}>
        {t(observationStateLabel(state))}
      </span>
    );
  }

  function renderVerification() {
    const raw = values.medicationVerified;
    if (raw === null || raw === undefined) {
      return (
        <span className="obs-display-state" data-state="NOT_ASSERTED">
          {t(verificationLabel('NOT_ASSERTED'))}
        </span>
      );
    }
    const v: VerificationValue = raw ? 'VERIFIED' : 'UNVERIFIED';
    return (
      <span className="obs-display-state" data-state={v}>
        {t(verificationLabel(v))}
      </span>
    );
  }

  const fieldsToShow = observationFields.filter(
    (field) => !correction || values[field] !== null || field === 'medicationVerified',
  );

  return (
    <dl className="note-values">
      {fieldsToShow.map((field) => {
        if (field === 'medicationVerified') {
          return (
            <div key={field} className="note-field-row">
              <dt>{t(fieldLabels[field])}</dt>
              <dd className="obs-display">{renderVerification()}</dd>
            </div>
          );
        }
        return (
          <div
            key={field}
            className={'note-field-row' + (field === 'freeText' ? ' note-field--wide' : '')}
          >
            <dt>{t(fieldLabels[field])}</dt>
            <dd className="obs-display">{renderObservation(field)}</dd>
          </div>
        );
      })}
    </dl>
  );
}
