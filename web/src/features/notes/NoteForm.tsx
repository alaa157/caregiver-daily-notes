import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../lib/api';
import { noteErrorMessage, observationFields, uncertainNoteSave, type AddendumInput, type CreateNoteInput, type ObservationField } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { Button } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { createInput, emptyNoteValues, fieldLabels, observationInput, validateNoteValues, type Corrections, type NoteErrors, type NoteValues } from './noteForm';

interface SharedProps {
  cancelTo: string;
  onSave: (input: CreateNoteInput | AddendumInput, key: string, signal: AbortSignal) => Promise<void>;
}
type Props = SharedProps & ({ mode: 'create'; recipients: Recipient[]; onOpen: (recipientId: string, date: string, signal: AbortSignal) => Promise<void> }
  | { mode: 'append' });

export function NoteForm(props: Props) {
  const { t } = useI18n();
  const [values, setValues] = useState<NoteValues>(emptyNoteValues);
  const [corrections, setCorrections] = useState<Corrections>({});
  const [errors, setErrors] = useState<NoteErrors>({});
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [activity, setActivity] = useState<'idle' | 'saving' | 'opening'>('idle');
  const [uncertain, setUncertain] = useState(false);
  const request = useRef<AbortController | null>(null);
  const attempt = useRef<{ key: string; input: CreateNoteInput | AddendumInput } | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const failureRef = useRef<HTMLDivElement>(null);
  const pending = activity !== 'idle';
  const locked = pending || uncertain;
  const recipient = props.mode === 'create' ? props.recipients.find(({ id }) => id === values.recipientId) : null;
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (failure || uncertain) failureRef.current?.focus(); }, [failure, uncertain]);
  useEffect(() => {
    if (activity !== 'saving' && !uncertain) return;
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', prevent);
    return () => window.removeEventListener('beforeunload', prevent);
  }, [activity, uncertain]);

  function showErrors(next: NoteErrors) {
    setErrors(next);
    const first = Object.keys(next)[0];
    if (first) formRef.current?.querySelector<HTMLElement>(first === 'form' ? '[data-correction]' : `#note-${first}`)?.focus();
    return !!first;
  }
  function metadataErrors(): NoteErrors {
    const result: NoteErrors = {};
    if (!recipient) result.recipientId = 'notesRequiredRecipient';
    const dateError = validateNoteValues(values).date;
    if (dateError) result.date = dateError;
    return result;
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current) return;
    if (!attempt.current) {
      const next = validateNoteValues(values, props.mode === 'append' ? corrections : undefined);
      if (props.mode === 'create' && !recipient?.active) next.recipientId = 'notesRequiredRecipient';
      if (showErrors(next)) return;
      const bytes = crypto.getRandomValues(new Uint8Array(16));
      attempt.current = { key: Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join(''),
        input: props.mode === 'create' ? createInput(values) : observationInput(values, corrections) };
    }
    const controller = new AbortController();
    request.current = controller;
    setFailure(null);
    setActivity('saving');
    try { await props.onSave(attempt.current.input, attempt.current.key, controller.signal); }
    catch (error) {
      if (!controller.signal.aborted) {
        const unknown = uncertainNoteSave(error) || (props.mode === 'append' && error instanceof ApiError && error.status === 409);
        setUncertain(unknown);
        setFailure(unknown ? 'notesUncertain' : noteErrorMessage(error));
        if (!unknown) attempt.current = null;
      }
    } finally {
      if (!controller.signal.aborted) { request.current = null; setActivity('idle'); }
    }
  }
  async function openExisting() {
    if (request.current || props.mode !== 'create' || showErrors(metadataErrors())) return;
    const controller = new AbortController();
    request.current = controller;
    setActivity('opening');
    setFailure(null);
    try { await props.onOpen(values.recipientId, values.date, controller.signal); }
    catch (error) {
      if (!controller.signal.aborted) setFailure(error instanceof ApiError && error.status === 404 ? 'notesMissing' : noteErrorMessage(error));
    } finally {
      if (!controller.signal.aborted) { request.current = null; setActivity('idle'); }
    }
  }
  function change(field: keyof NoteValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    if (!uncertain) setFailure(null);
  }
  const hintFor = (field: ObservationField) => field === 'freeText' ? 'notesFreeTextHint' : field === 'medicationStatus' || field === 'medicationVerified' ? 'notesMedicationHint' : null;
  function fieldInput(field: ObservationField) {
    const attributes = { id: `note-${field}`, name: field, value: values[field], disabled: locked,
      'aria-invalid': !!errors[field], 'aria-describedby': [hintFor(field) ? `note-${field}-hint` : '', errors[field] ? `note-${field}-error` : ''].filter(Boolean).join(' ') || undefined,
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => change(field, event.target.value) };
    if (field === 'medicationStatus' || field === 'medicationVerified' || field === 'fallReported') return <select {...attributes}>
      <option value="">{t(props.mode === 'append' ? 'notesChooseValue' : 'notesNotRecorded')}</option>
      {field === 'medicationStatus' ? <><option value="TAKEN">{t('notesTaken')}</option><option value="MISSED">{t('notesMissed')}</option><option value="UNKNOWN">{t('notesUnknown')}</option></>
        : <><option value="true">{t('notesYes')}</option><option value="false">{t('notesNo')}</option></>}
    </select>;
    if (field === 'freeText') return <textarea {...attributes} dir="auto" rows={6} maxLength={20000} autoCapitalize="none" autoCorrect="off" spellCheck={false} />;
    if (field === 'painScore') return <input {...attributes} type="number" min={0} max={10} step={1} inputMode="numeric" />;
    return <input {...attributes} type="text" dir="auto" autoComplete="off" autoCapitalize="none" autoCorrect="off" spellCheck={false} maxLength={40} />;
  }

  return <form ref={formRef} className="note-form" aria-label={t(props.mode === 'create' ? 'notesCreate' : 'notesAppend')} noValidate onSubmit={(event) => { void submit(event); }}>
    {(failure || uncertain) && <div ref={failureRef} className="note-error" role="alert" tabIndex={-1}>
      <p>{t(uncertain ? 'notesUncertain' : failure!)}</p>
      {uncertain && failure && failure !== 'notesUncertain' && <p>{t(failure)}</p>}
      {props.mode === 'append' && <Link to={props.cancelTo}>{t('notesReview')}</Link>}
    </div>}
    {props.mode === 'create' && <div className="note-metadata note-grid">
      <div className="note-field"><label htmlFor="note-recipientId">{t('notesRecipient')}</label>
        <select id="note-recipientId" name="recipientId" value={values.recipientId} disabled={locked} required aria-invalid={!!errors.recipientId}
          aria-describedby={errors.recipientId ? 'note-recipientId-error' : undefined} onChange={(event) => change('recipientId', event.target.value)}>
          <option value="">{t('notesChooseRecipient')}</option>
          {props.recipients.map((record) => <option key={record.id} value={record.id}>{record.name}{record.active ? '' : ` (${t('recipientInactive')})`}</option>)}
        </select>{errors.recipientId && <FieldError id="note-recipientId-error" message={t(errors.recipientId)} />}
      </div>
      <div className="note-field"><label htmlFor="note-date">{t('notesDate')}</label>
        <input id="note-date" name="date" type="date" dir="ltr" value={values.date} disabled={locked} required aria-invalid={!!errors.date}
          aria-describedby={'note-date-hint' + (errors.date ? ' note-date-error' : '')} onChange={(event) => change('date', event.target.value)} />
        <p id="note-date-hint" className="note-hint">{t('notesDateHint')}</p>{errors.date && <FieldError id="note-date-error" message={t(errors.date)} />}
      </div>
      {recipient && !recipient.active && <p className="note-notice">{t('notesInactive')}</p>}
    </div>}
    <fieldset className="note-observations"><legend>{t('notesObservations')}</legend>
      <p className="note-hint">{t(props.mode === 'append' ? 'notesAppendDescription' : 'notesObservationHint')}</p>
      {errors.form && <FieldError id="note-corrections-error" message={t(errors.form)} />}
      <div className="note-grid">{observationFields.map((field) => <div key={field} className={'note-field' + (field === 'freeText' ? ' note-field--wide' : '')}>
        {props.mode === 'append' && <label className="note-correction"><input type="checkbox" data-correction disabled={locked} checked={!!corrections[field]}
          aria-describedby={errors.form ? 'note-corrections-error' : undefined} onChange={(event) => { setCorrections((current) => ({ ...current, [field]: event.target.checked })); setErrors({}); }} />
          {t('notesCorrect')}: {t(fieldLabels[field])}</label>}
        {(props.mode === 'create' || corrections[field]) && <><label htmlFor={`note-${field}`}>{t(fieldLabels[field])}</label>{fieldInput(field)}
          {hintFor(field) && <p id={`note-${field}-hint`} className="note-hint">{t(hintFor(field)!)}</p>}
          {errors[field] && <FieldError id={`note-${field}-error`} message={t(errors[field]!)} />}</>}
      </div>)}</div>
    </fieldset>
    <div className="note-actions">
      <Button type="submit" disabled={pending || (props.mode === 'create' && !!recipient && !recipient.active)}>
        {t(activity === 'saving' ? 'notesSaving' : uncertain ? 'notesRetrySave' : props.mode === 'create' ? 'notesSave' : 'notesSaveAppend')}
      </Button>
      {props.mode === 'create' && <Button variant="secondary" disabled={pending} onClick={() => { void openExisting(); }}>{t(activity === 'opening' ? 'notesOpening' : 'notesOpen')}</Button>}
      <Link className="button button--text" to={props.cancelTo} onClick={(event) => { if ((activity === 'saving' || uncertain) && !window.confirm(t('notesLeaving'))) event.preventDefault(); }}>{t('notesCancel')}</Link>
    </div>
    <p role="status" className="note-progress">{activity === 'saving' ? t('notesSaving') : activity === 'opening' ? t('notesOpening') : ''}</p>
  </form>;
}
