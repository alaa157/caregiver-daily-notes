import { type FormEvent, useId, useRef, useState } from 'react';
import type { CreateStructuredPlanInput, PlanChange } from '../../api/plans';
import type { NoteListItem } from '../../api/notes';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { actionLabel, parameterLabel } from './PlanEditor';

const actionOptions = [
  'SCHEDULE_CHECK_IN',
  'UPDATE_MEAL_REMINDER',
  'LOG_MOBILITY_GOAL',
  'CAREGIVER_EDUCATION_TOPIC',
  'ESCALATE_TO_DOCTOR',
] as const;
type DraftAction = typeof actionOptions[number];
type DraftRow = { action: DraftAction; params: Record<string, string>; reason: string; evidenceNoteIds: string[] };
type RowErrors = { params: Record<string, MessageKey>; reason?: MessageKey; evidence?: MessageKey };
const actionParameters: Record<DraftAction, readonly string[]> = {
  SCHEDULE_CHECK_IN: ['withinDays'],
  UPDATE_MEAL_REMINDER: ['meal', 'time'],
  LOG_MOBILITY_GOAL: ['activity', 'frequency'],
  CAREGIVER_EDUCATION_TOPIC: ['topic'],
  ESCALATE_TO_DOCTOR: ['reason'],
};
function isDraftAction(value: string): value is DraftAction {
  return actionOptions.some((action) => action === value);
}

function createRow(action: DraftAction = 'SCHEDULE_CHECK_IN'): DraftRow {
  return {
    action,
    params: Object.fromEntries(actionParameters[action].map((key) => [key, key === 'withinDays' ? '3' : ''])),
    reason: '',
    evidenceNoteIds: [],
  };
}

function parameterError(key: string, value: string): MessageKey | undefined {
  if (!value.trim() || value.length > 500) return 'plansDraftParamRequired';
  if (key === 'withinDays' && (!/^\d+$/.test(value) || Number(value) < 1 || Number(value) > 365)) return 'plansDraftDaysInvalid';
  if (key === 'time' && !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) return 'plansDraftTimeInvalid';
  return undefined;
}

function validateRow(row: DraftRow, availableEvidenceIds: ReadonlySet<string>): RowErrors {
  const expected = actionParameters[row.action];
  const params: Record<string, MessageKey> = {};
  for (const key of expected) {
    const error = parameterError(key, row.params[key] ?? '');
    if (error) params[key] = error;
  }
  if (Object.keys(row.params).length !== expected.length || expected.some((key) => !Object.hasOwn(row.params, key))) {
    for (const key of expected) params[key] ??= 'plansDraftParamRequired';
  }
  return {
    params,
    ...(row.reason.trim() && row.reason.length <= 500 ? {} : { reason: 'plansDraftReasonRequired' as const }),
    ...(row.evidenceNoteIds.length && row.evidenceNoteIds.every((id) => availableEvidenceIds.has(id))
      ? {} : { evidence: 'plansDraftEvidenceRequired' as const }),
  };
}

export function PlanDraftForm({ evidenceNotes, disabled, onSave }: {
  evidenceNotes: NoteListItem[];
  disabled: boolean;
  onSave: (input: CreateStructuredPlanInput) => void;
}) {
  const { t } = useI18n();
  const [rows, setRows] = useState<DraftRow[]>([createRow()]);
  const [attempted, setAttempted] = useState(false);
  const form = useRef<HTMLFormElement>(null);
  const id = useId();
  const availableEvidenceIds = new Set(evidenceNotes.map((note) => note.id));
  const errors = rows.map((row) => validateRow(row, availableEvidenceIds));

  function updateRow(index: number, update: (row: DraftRow) => DraftRow) {
    setRows((current) => current.map((row, at) => at === index ? update(row) : row));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled) return;
    setAttempted(true);
    if (!rows.length || errors.some((row) => Object.keys(row.params).length > 0 || row.reason || row.evidence)) {
      form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
      return;
    }
    const changes: PlanChange[] = rows.map((row) => ({
      action: row.action,
      params: Object.fromEntries(actionParameters[row.action].map((key) => [key, row.params[key].trim()])),
      reason: row.reason.trim(),
      evidenceNoteIds: [...row.evidenceNoteIds],
    }));
    onSave({ schemaVersion: 1, changes, medications: [] });
  }

  return <form ref={form} className="plan-editor" noValidate onSubmit={submit} aria-labelledby={`${id}-heading`}>
    <h3 id={`${id}-heading`}>{t('plansCreateDraft')}</h3>
    <p id={`${id}-hint`}>{t('plansCreateDraftHint')}</p>
    {attempted && !rows.length && <p className="note-error" role="alert">{t('plansDraftNoActions')}</p>}
    {rows.map((row, index) => {
      const rowId = `${id}-${index}`;
      const rowErrors = errors[index];
      return <fieldset className="plan-edit-fields" disabled={disabled} key={index}>
        <legend>{index + 1}. {t(actionLabel(row.action))}</legend>
        <div className="note-field">
          <label htmlFor={`${rowId}-action`}>{t('plansDraftAction')}</label>
          <select id={`${rowId}-action`} value={row.action} aria-describedby={`${id}-hint`}
            onChange={(event) => {
              const action = event.target.value;
              if (isDraftAction(action)) updateRow(index, (current) => ({
                ...current, ...createRow(action), reason: current.reason, evidenceNoteIds: current.evidenceNoteIds,
              }));
            }}>
            {actionOptions.map((action) => <option value={action} key={action}>{t(actionLabel(action))}</option>)}
          </select>
        </div>
        {actionParameters[row.action].map((key) => {
          const fieldId = `${rowId}-${key}`;
          const error = rowErrors.params[key];
          const message = key === 'reason' ? 'plansParamEscalationReason' : parameterLabel(key);
          return <div className="note-field" key={key}>
            <label htmlFor={fieldId}>{t(message)}</label>
            <input id={fieldId} dir="auto" type={key === 'withinDays' ? 'number' : key === 'time' ? 'time' : 'text'}
              min={key === 'withinDays' ? 1 : undefined} max={key === 'withinDays' ? 365 : undefined}
              step={key === 'withinDays' ? 1 : undefined} maxLength={key === 'withinDays' || key === 'time' ? undefined : 500}
              value={row.params[key]} required autoComplete="off"
              aria-invalid={attempted && !!error}
              aria-describedby={attempted && error ? `${fieldId}-error` : `${id}-hint`}
              onChange={(event) => { const value = event.target.value; updateRow(index, (current) => ({ ...current, params: { ...current.params, [key]: value } })); }} />
            {attempted && error && <p id={`${fieldId}-error`} className="note-error" role="alert">{t(error)}</p>}
          </div>;
        })}
        <div className="note-field">
          <label htmlFor={`${rowId}-reason`}>{t('plansDraftReason')}</label>
          <textarea id={`${rowId}-reason`} dir="auto" value={row.reason} required maxLength={500}
            aria-invalid={attempted && !!rowErrors.reason}
            aria-describedby={attempted && rowErrors.reason ? `${rowId}-reason-error` : `${id}-hint`}
            onChange={(event) => { const reason = event.target.value; updateRow(index, (current) => ({ ...current, reason })); }} />
          {attempted && rowErrors.reason && <p id={`${rowId}-reason-error`} className="note-error" role="alert">{t(rowErrors.reason)}</p>}
        </div>
        <fieldset className="plan-edit-fields" tabIndex={attempted && rowErrors.evidence ? -1 : undefined} aria-invalid={attempted && !!rowErrors.evidence}
          aria-describedby={attempted && rowErrors.evidence ? `${rowId}-evidence-error` : `${id}-hint`}>
          <legend>{t('plansDraftEvidence')}</legend>
          <p className="note-hint">{t('plansDraftEvidenceHint')}</p>
          {!evidenceNotes.length && <p>{t('plansDraftNoEvidence')}</p>}
          {evidenceNotes.map((note) => {
            const evidenceId = `${rowId}-evidence-${note.id}`;
            return <div className="note-field" key={note.id}>
              <label htmlFor={evidenceId}>
                <input id={evidenceId} type="checkbox" checked={row.evidenceNoteIds.includes(note.id)}
                  onChange={(event) => updateRow(index, (current) => ({
                    ...current,
                    evidenceNoteIds: event.target.checked
                      ? [...current.evidenceNoteIds, note.id]
                      : current.evidenceNoteIds.filter((value) => value !== note.id),
                  }))} />
                <bdi>{note.date} — {note.id}</bdi>
              </label>
            </div>;
          })}
          {attempted && rowErrors.evidence && <p id={`${rowId}-evidence-error`} className="note-error" role="alert">{t(rowErrors.evidence)}</p>}
        </fieldset>
        <div className="note-actions">
          <Button type="button" variant="secondary" disabled={disabled} onClick={() => setRows((current) => current.filter((_, at) => at !== index))}>
            {t('plansDraftRemoveChange')}
          </Button>
        </div>
      </fieldset>;
    })}
    <section className="plan-medications" aria-label={t('plansMedications')}>
      <h4>{t('plansMedications')}</h4><p>{t('plansDraftMedicationHint')}</p>
    </section>
    <div className="note-actions">
      <Button type="button" variant="secondary" disabled={disabled} onClick={() => setRows((current) => [...current, createRow()])}>
        {t('plansDraftAddChange')}
      </Button>
      <Button type="submit" disabled={disabled}>{t('plansDraftSave')}</Button>
    </div>
  </form>;
}
