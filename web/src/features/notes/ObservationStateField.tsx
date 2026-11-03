import {
  OBSERVATION_STATES,
  SELECTABLE_OBSERVATION_STATES,
  isObservationState,
  isSelectableObservationState,
  isVerificationValue,
  type Observation,
  type ObservationState,
  type VerificationValue,
  VERIFICATION_VALUES,
} from '../../api/observation';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

const stateLabelKeys: Record<ObservationState, MessageKey> = {
  RECORDED: 'obsStateRecorded',
  UNKNOWN: 'obsStateUnknown',
  NOT_RECORDED: 'obsStateNotRecorded',
  NOT_APPLICABLE: 'obsStateNotApplicable',
  LEGACY_AMBIGUOUS: 'obsStateLegacyAmbiguous',
};

const stateHintKeys: Record<ObservationState, MessageKey> = {
  RECORDED: 'obsStateRecordedHint',
  UNKNOWN: 'obsStateUnknownHint',
  NOT_RECORDED: 'obsStateNotRecordedHint',
  NOT_APPLICABLE: 'obsStateNotApplicableHint',
  LEGACY_AMBIGUOUS: 'obsStateLegacyAmbiguousHint',
};

const verificationLabelKeys: Record<VerificationValue, MessageKey> = {
  VERIFIED: 'obsVerificationVerified',
  UNVERIFIED: 'obsVerificationUnverified',
  NOT_ASSERTED: 'obsVerificationNotAsserted',
};

export function observationStateLabel(state: ObservationState): MessageKey {
  return stateLabelKeys[state];
}

export function observationStateHint(state: ObservationState): MessageKey {
  return stateHintKeys[state];
}

export function verificationLabel(value: VerificationValue): MessageKey {
  return verificationLabelKeys[value];
}

type ValueEditorProps = {
  fieldId: string;
  disabled?: boolean;
  children: React.ReactNode;
};

function ValueEditor({ fieldId, disabled, children }: ValueEditorProps) {
  return (
    <div
      className="obs-value-editor"
      data-obs-value-for={fieldId}
      aria-hidden={disabled ? true : undefined}
      style={disabled ? { display: 'none' } : undefined}
    >
      {children}
    </div>
  );
}

export type ObservationStateFieldProps<T> = {
  id: string;
  label: string;
  observation: Observation<T>;
  onChange: (next: Observation<T>) => void;
  renderValue: (props: {
    id: string;
    value: T | null | undefined;
    onChange: (value: T | null) => void;
    disabled: boolean;
    describedBy?: string;
  }) => React.ReactNode;
  verification?: VerificationValue | null;
  onVerificationChange?: (value: VerificationValue | null) => void;
  error?: string;
  hint?: string;
  locked?: boolean;
  allowLegacy?: boolean;
};

/**
 * Reusable observation-state editor.
 * - State selector is always visible.
 * - Typed value editor appears only for RECORDED.
 * - Switching away from RECORDED clears the submitted value path.
 * - Verification is independent and never auto-derived from state.
 */
export function ObservationStateField<T>({
  id,
  label,
  observation,
  onChange,
  renderValue,
  verification,
  onVerificationChange,
  error,
  hint,
  locked = false,
  allowLegacy = false,
}: ObservationStateFieldProps<T>) {
  const { t } = useI18n();
  const stateId = `${id}-state`;
  const valueId = `${id}-value`;
  const errorId = error ? `${id}-error` : undefined;
  const hintId = hint ? `${id}-hint` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  const selectable = allowLegacy
    ? (OBSERVATION_STATES as readonly ObservationState[])
    : SELECTABLE_OBSERVATION_STATES;

  function setState(nextState: ObservationState) {
    if (locked) return;
    if (nextState === 'RECORDED') {
      onChange({
        state: 'RECORDED',
        value: observation.value,
        rawValue: observation.rawValue,
        verification: observation.verification,
      });
    } else {
      onChange({
        state: nextState,
        value: undefined,
        rawValue: observation.rawValue,
        verification: observation.verification,
      });
    }
  }

  function setValue(value: T | null) {
    if (locked) return;
    onChange({
      state: 'RECORDED',
      value,
      rawValue: observation.rawValue,
      verification: observation.verification,
    });
  }

  const isRecorded = observation.state === 'RECORDED';
  const isLegacy = observation.state === 'LEGACY_AMBIGUOUS';

  return (
    <div
      className={`obs-field${error ? ' obs-field--error' : ''}${isLegacy ? ' obs-field--legacy' : ''}`}
      data-obs-state={observation.state}
    >
      <div className="obs-field-header">
        <span className="obs-field-label" id={`${id}-label`}>
          {label}
        </span>
        {isLegacy && (
          <span className="obs-legacy-badge" role="status">
            {t('obsStateLegacyAmbiguous')}
          </span>
        )}
      </div>

      <div className="obs-state-group" role="group" aria-labelledby={`${id}-label`}>
        <label htmlFor={stateId} className="obs-state-label">
          {t('obsStateLabel')}
        </label>
        <select
          id={stateId}
          className="obs-state-select"
          value={observation.state}
          disabled={locked || (isLegacy && !allowLegacy)}
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
          onChange={(e) => {
            const v = e.target.value;
            if (isSelectableObservationState(v) || (allowLegacy && isObservationState(v))) {
              setState(v as ObservationState);
            }
          }}
        >
          {selectable.map((s) => (
            <option key={s} value={s}>
              {t(stateLabelKeys[s])}
            </option>
          ))}
        </select>
        <p className="obs-state-hint" id={`${id}-state-hint`}>
          {t(stateHintKeys[observation.state])}
        </p>
      </div>

      <ValueEditor fieldId={id} disabled={!isRecorded}>
        {renderValue({
          id: valueId,
          value: observation.value,
          onChange: setValue,
          disabled: locked || !isRecorded,
          describedBy,
        })}
      </ValueEditor>

      {onVerificationChange !== undefined && (
        <div className="obs-verification">
          <label htmlFor={`${id}-verification`} className="obs-verification-label">
            {t('obsVerificationLabel')}
          </label>
          <select
            id={`${id}-verification`}
            className="obs-verification-select"
            value={verification ?? 'NOT_ASSERTED'}
            disabled={locked}
            onChange={(e) => {
              const v = e.target.value;
              if (isVerificationValue(v)) onVerificationChange(v);
            }}
          >
            {VERIFICATION_VALUES.map((v) => (
              <option key={v} value={v}>
                {t(verificationLabelKeys[v])}
              </option>
            ))}
          </select>
          <p className="obs-verification-hint">{t('obsVerificationHint')}</p>
        </div>
      )}

      {hint && (
        <p id={hintId} className="note-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="note-error-text" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
