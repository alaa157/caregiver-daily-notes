import { observationFields } from '../../api/notes';
import type {
  NoteObservations,
  Observation,
  ObservationState,
  StructuredObservationField,
  VerificationValue,
} from '../../api/observation';
import { useI18n } from '../../i18n/I18nProvider';
import { fieldLabels } from './noteForm';
import {
  observationStateLabel,
  verificationLabel,
} from './ObservationStateField';

export function ObservationValues({
  values,
  providedFields,
  verificationProvided,
  correction = false,
}: {
  values: NoteObservations;
  providedFields?: StructuredObservationField[];
  verificationProvided?: boolean;
  correction?: boolean;
}) {
  const { t, language } = useI18n();

  function renderTypedValue(field: StructuredObservationField, value: unknown): string {
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

  function renderObservation(field: StructuredObservationField, observation: Observation<unknown>) {
    const state = observation.state as ObservationState;
    if (state === 'RECORDED') {
      return (
        <>
          <span className="obs-display-state" data-state="RECORDED">
            {t(observationStateLabel('RECORDED'))}
          </span>
          <span className="obs-display-value note-verbatim" dir="auto">
            {renderTypedValue(field, observation.value)}
          </span>
        </>
      );
    }
    if (state === 'LEGACY_AMBIGUOUS') {
      return (
        <>
          <span className="obs-display-state obs-display-state--legacy" data-state="LEGACY_AMBIGUOUS">
            {t(observationStateLabel('LEGACY_AMBIGUOUS'))}
          </span>
          {observation.rawValue !== null && observation.rawValue !== undefined && (
            <span className="obs-display-raw note-verbatim" dir="auto" title={t('obsLegacyRawHint')}>
              {renderTypedValue(field, observation.rawValue)}
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

  const fieldsToShow = observationFields.filter(
    (field) => !correction || providedFields?.includes(field),
  );
  const verification: VerificationValue = values.medicationVerification;

  return (
    <dl className="note-values">
      {fieldsToShow.map((field) => (
        <div
          key={field}
          className={'note-field-row' + (field === 'freeText' ? ' note-field--wide' : '')}
        >
          <dt>{t(fieldLabels[field])}</dt>
          <dd className="obs-display">{renderObservation(field, values[field])}</dd>
        </div>
      ))}
      {(!correction || verificationProvided) && (
        <div key="medicationVerification" className="note-field-row">
          <dt>{t('notesMedicationVerified')}</dt>
          <dd className="obs-display">
            <span className="obs-display-state" data-state={verification}>
              {t(verificationLabel(verification))}
            </span>
          </dd>
        </div>
      )}
    </dl>
  );
}
