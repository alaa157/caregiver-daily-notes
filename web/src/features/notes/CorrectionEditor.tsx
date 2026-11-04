import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { observationFields, type CorrectionOperation, type FieldCorrection, type ObservationField } from '../../api/notes';
import { Button } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { fieldLabels } from './noteForm';

type Props = { onSave: (entries: FieldCorrection[], key: string, signal: AbortSignal) => Promise<void>; onCancel: () => void };
type FieldErrors = Partial<Record<ObservationField | 'form', MessageKey>>;
const operations: CorrectionOperation[] = ['SET', 'CLEAR', 'UNKNOWN', 'NOT_RECORDED'];
const emptyValues = () => Object.fromEntries(observationFields.map((field) => [field, ''])) as Record<ObservationField, string>;

export function CorrectionEditor({ onSave, onCancel }: Props) {
  const { t } = useI18n();
  const [selected, setSelected] = useState<Partial<Record<ObservationField, CorrectionOperation>>>({});
  const [values, setValues] = useState<Record<ObservationField, string>>(emptyValues);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState(false);
  const [saving, setSaving] = useState(false);
  const request = useRef<AbortController | null>(null);
  const retryKey = useRef<string | null>(null);

  function update(field: ObservationField, operation: CorrectionOperation | undefined) {
    setSelected((current) => ({ ...current, [field]: operation }));
    setErrors((current) => ({ ...current, [field]: undefined, form: undefined }));
    setFailure(false);
    retryKey.current = null;
  }

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    if (!observationFields.some((field) => selected[field])) next.form = 'notesCorrectionSelectOne';
    for (const field of observationFields) {
      if (selected[field] !== 'SET') continue;
      const value = values[field];
      if ((field === 'painScore' && (!/^\d+$/.test(value) || Number(value) > 10))
        || ((field === 'medicationStatus' || field === 'fallReported') && value === '')) {
        next[field] = field === 'painScore' ? 'notesCorrectionPainInvalid' : 'notesCorrectionValueRequired';
      } else if ((field === 'mood' || field === 'appetite' || field === 'sleep' || field === 'mobility') && value.length > 40) {
        next[field] = 'notesTextLimit';
      } else if (field === 'freeText' && value.length > 20000) next[field] = 'notesFreeTextLimit';
      else if (field === 'medicationStatus' && !['TAKEN', 'MISSED'].includes(value)) next[field] = 'notesCorrectionValueRequired';
      else if (field === 'fallReported' && !['true', 'false'].includes(value)) next[field] = 'notesCorrectionValueRequired';
    }
    return next;
  }

  function correctionValue(field: ObservationField, raw: string): string | number | boolean {
    if (field === 'painScore') return Number(raw);
    if (field === 'fallReported') return raw === 'true';
    return raw;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || saving) return;
    const nextErrors = validate();
    setErrors(nextErrors);
    const firstError = observationFields.find((field) => nextErrors[field]);
    if (firstError) document.getElementById(`correction-value-${firstError}`)?.focus();
    if (Object.keys(nextErrors).length) return;
    const entries = observationFields.flatMap((field): FieldCorrection[] => {
      const operation = selected[field];
      if (!operation) return [];
      return operation === 'SET' ? [{ field, operation, value: correctionValue(field, values[field]) }] : [{ field, operation }];
    });
    retryKey.current ??= Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('');
    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    setFailure(false);
    try { await onSave(entries, retryKey.current, controller.signal); }
    catch { if (!controller.signal.aborted) setFailure(true); }
    finally { request.current = null; setSaving(false); }
  }

  function valueControl(field: ObservationField) {
    const id = `correction-value-${field}`;
    const common = { id, value: values[field], disabled: saving, 'aria-invalid': !!errors[field],
      'aria-describedby': errors[field] ? `${id}-error` : undefined,
      onChange: (event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        setValues((current) => ({ ...current, [field]: event.target.value }));
        setErrors((current) => ({ ...current, [field]: undefined })); setFailure(false); retryKey.current = null;
      } };
    if (field === 'medicationStatus') return <select {...common}><option value="">{t('notesCorrectionChooseValue')}</option>
      <option value="TAKEN">{t('notesTaken')}</option><option value="MISSED">{t('notesMissed')}</option></select>;
    if (field === 'fallReported') return <select {...common}><option value="">{t('notesCorrectionChooseValue')}</option>
      <option value="true">{t('notesYes')}</option><option value="false">{t('notesNo')}</option></select>;
    if (field === 'painScore') return <input {...common} type="number" min={0} max={10} step={1} inputMode="numeric" />;
    if (field === 'freeText') return <textarea {...common} dir="auto" rows={4} maxLength={20000} />;
    return <input {...common} type="text" dir="auto" maxLength={40} />;
  }

  return <form className="note-correction-editor" aria-label={t('notesCorrectionEditor')} noValidate onSubmit={(event) => { void submit(event); }}>
    <p className="note-hint">{t('notesCorrectionDescription')}</p>
    {failure && <p className="note-error" role="alert">{t('notesCorrectionFailure')}</p>}
    {errors.form && <FieldError id="correction-form-error" message={t(errors.form)} />}
    <div className="correction-editor-fields">{observationFields.map((field) => {
      const operationId = `correction-operation-${field}`;
      return <fieldset className="correction-editor-field" key={field}>
        <legend>{t(fieldLabels[field])}</legend>
        <label htmlFor={operationId}>{t('notesCorrectionOperation')}</label>
        <select id={operationId} value={selected[field] ?? ''} disabled={saving} aria-describedby={errors.form ? 'correction-form-error' : undefined}
          onChange={(event) => update(field, (event.target.value || undefined) as CorrectionOperation | undefined)}>
          <option value="">{t('notesCorrectionChooseOperation')}</option>
          {operations.map((operation) => <option key={operation} value={operation}>{t(operation === 'SET' ? 'notesCorrectionSet' : operation === 'CLEAR' ? 'notesCorrectionClear' : operation === 'UNKNOWN' ? 'notesCorrectionUnknown' : 'notesCorrectionNotRecorded')}</option>)}
        </select>
        {selected[field] === 'SET' && <><label htmlFor={`correction-value-${field}`}>{t('notesCorrectionValue')}</label>{valueControl(field)}
          {errors[field] && <FieldError id={`correction-value-${field}-error`} message={t(errors[field]!)} />}</>}
      </fieldset>;
    })}</div>
    <div className="note-actions">
      <Button type="submit" disabled={saving}>{t(saving ? 'notesCorrectionSaving' : 'notesCorrectionSubmit')}</Button>
      <Button type="button" variant="secondary" disabled={saving} onClick={onCancel}>{t('notesCorrectionCancel')}</Button>
    </div>
    <p className="note-progress" role="status">{saving ? t('notesCorrectionSaving') : ''}</p>
  </form>;
}
