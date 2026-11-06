import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ApiError, useApiClient } from '../../lib/api';
import { createMedicationsApi, medicationErrorMessage, validateMedication, type MedicationFieldErrors, type MedicationInput, type MedicationRecord } from '../../api/medications';
import { Button } from '../../components/Button';
import { EmptyState, ErrorState, FieldError, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

type ListState = { recipientId: string; status: 'loading' }
  | { recipientId: string; status: 'error'; error: unknown }
  | { recipientId: string; status: 'ready'; data: MedicationRecord[] };
type Editor = { medicationId: string | null; input: MedicationInput };
const emptyInput = (): MedicationInput => ({ displayName: '', doseText: '', scheduleText: '' });

export function MedicationListPanel({ recipientId, canEdit }: { recipientId: string; canEdit: boolean }) {
  const { t } = useI18n();
  const client = useApiClient();
  const api = useMemo(() => createMedicationsApi(client), [client]);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<ListState>({ recipientId, status: 'loading' });
  const [editor, setEditor] = useState<Editor | null>(null);
  const [fieldErrors, setFieldErrors] = useState<MedicationFieldErrors>({});
  const [formError, setFormError] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);
  const [archivingId, setArchivingId] = useState<string | null>(null);
  const [archiveError, setArchiveError] = useState<{ id: string; key: MessageKey } | null>(null);
  const mutation = useRef<AbortController | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    setState({ recipientId, status: 'loading' });
    void api.list(recipientId, controller.signal).then(
      (data) => { if (!controller.signal.aborted) setState({ recipientId, status: 'ready', data }); },
      (error: unknown) => { if (!controller.signal.aborted) setState({ recipientId, status: 'error', error }); },
    );
    return () => {
      controller.abort();
      mutation.current?.abort();
      mutation.current = null;
    };
  }, [api, recipientId, revision]);

  useEffect(() => {
    setEditor(null);
    setFieldErrors({});
    setFormError(null);
    setNotice(null);
    setArchiveError(null);
    setSaving(false);
    setArchivingId(null);
  }, [recipientId]);

  useEffect(() => {
    if (!canEdit) {
      setEditor(null);
      setFormError(null);
      setArchiveError(null);
    }
  }, [canEdit]);
  useEffect(() => { if (formError) errorRef.current?.focus(); }, [formError]);

  function openCreate() {
    setEditor({ medicationId: null, input: emptyInput() });
    setFieldErrors({});
    setFormError(null);
    setNotice(null);
  }
  function openEdit(record: MedicationRecord) {
    setEditor({ medicationId: record.id, input: {
      displayName: record.displayName, doseText: record.doseText, scheduleText: record.scheduleText,
    } });
    setFieldErrors({});
    setFormError(null);
    setNotice(null);
  }
  function closeEditor() {
    if (mutation.current) return;
    setEditor(null);
    setFieldErrors({});
    setFormError(null);
  }
  function change(field: keyof MedicationInput, value: string) {
    setEditor((current) => current ? { ...current, input: { ...current.input, [field]: value } } : current);
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor || mutation.current || !canEdit) return;
    const errors = validateMedication(editor.input);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      const invalid = Object.keys(errors)[0] as keyof MedicationInput;
      formRef.current?.querySelector<HTMLElement>(`[name="${invalid}"]`)?.focus();
      return;
    }
    const controller = new AbortController();
    mutation.current = controller;
    setSaving(true);
    setNotice(null);
    try {
      const saved = editor.medicationId
        ? await api.update(recipientId, editor.medicationId, editor.input, controller.signal)
        : await api.create(recipientId, editor.input, controller.signal);
      if (controller.signal.aborted) return;
      setEditor(null);
      setFieldErrors({});
      setNotice(editor.medicationId ? 'medicationUpdateSuccess' : 'medicationCreateSuccess');
      setState((current) => current.recipientId === recipientId && current.status === 'ready'
        ? { ...current, data: editor.medicationId
          ? current.data.map((record) => record.id === saved.id ? saved : record)
          : [...current.data, saved] }
        : current);
      mutation.current = null;
      setRevision((value) => value + 1);
    } catch (error) {
      if (!controller.signal.aborted) {
        setFormError(medicationErrorMessage(error));
        if (error instanceof ApiError && error.status === 409) setRevision((value) => value + 1);
      }
    } finally {
      if (!controller.signal.aborted) {
        mutation.current = null;
        setSaving(false);
      }
    }
  }

  async function archive(record: MedicationRecord) {
    if (mutation.current || !canEdit || !record.active) return;
    const controller = new AbortController();
    mutation.current = controller;
    setArchivingId(record.id);
    setArchiveError(null);
    setNotice(null);
    try {
      const archived = await api.archive(recipientId, record.id, controller.signal);
      if (controller.signal.aborted) return;
      setState((current) => current.recipientId === recipientId && current.status === 'ready'
        ? { ...current, data: current.data.map((item) => item.id === archived.id ? archived : item) }
        : current);
      setNotice('medicationArchiveSuccess');
      mutation.current = null;
      setRevision((value) => value + 1);
    } catch (error) {
      if (!controller.signal.aborted) {
        setArchiveError({ id: record.id, key: medicationErrorMessage(error) });
        if (error instanceof ApiError && error.status === 409) setRevision((value) => value + 1);
      }
    } finally {
      if (!controller.signal.aborted) {
        mutation.current = null;
        setArchivingId(null);
      }
    }
  }

  const visibleState: ListState = state.recipientId === recipientId ? state : { recipientId, status: 'loading' };
  const editedRecord = editor?.medicationId && visibleState.status === 'ready'
    ? visibleState.data.find((record) => record.id === editor.medicationId) : undefined;
  const editorAllowed = !!canEdit && (!editor?.medicationId || editedRecord?.active === true || formError === 'medicationConflict');
  const editorReadOnly = saving || (!!editor?.medicationId && editedRecord?.active !== true);

  return <section className="medication-panel" aria-labelledby="medication-panel-heading">
    <div className="medication-panel-heading">
      <h2 id="medication-panel-heading">{t('medicationSectionTitle')}</h2>
      {canEdit && visibleState.status === 'ready' && visibleState.data.length > 0 && !editor
        && <Button type="button" onClick={openCreate}>{t('medicationCreate')}</Button>}
    </div>
    {notice && <p role="status" className="recipient-success">{t(notice)}</p>}
    {visibleState.status === 'loading' && <LoadingState label={t('medicationLoading')} />}
    {visibleState.status === 'error' && <ErrorState description={t(medicationErrorMessage(visibleState.error))}
      onRetry={() => setRevision((value) => value + 1)} />}
    {visibleState.status === 'ready' && visibleState.data.length === 0 && <>
      <EmptyState title={t('medicationEmptyTitle')} description={t('medicationEmptyDescription')} />
      {canEdit && !editor && <div className="medication-empty-action"><Button type="button" onClick={openCreate}>{t('medicationCreate')}</Button></div>}
    </>}
    {visibleState.status === 'ready' && visibleState.data.length > 0 && <ul className="medication-list" aria-label={t('medicationSectionTitle')}>
      {visibleState.data.map((record) => <li className="medication-card" key={record.id}>
        <div className="medication-record-heading"><h3><bdi dir="auto">{record.displayName}</bdi></h3>
          <span className={`recipient-status ${record.active ? '' : 'recipient-status--archived'}`}>
            <span aria-hidden="true" className="status-dot" />{t(record.active ? 'medicationActive' : 'medicationArchived')}
          </span>
        </div>
        <dl className="recipient-details medication-details">
          <div><dt>{t('medicationDose')}</dt><dd><bdi dir="auto">{record.doseText}</bdi></dd></div>
          <div><dt>{t('medicationSchedule')}</dt><dd><bdi dir="auto">{record.scheduleText}</bdi></dd></div>
        </dl>
        {canEdit && record.active && <div className="recipient-actions">
          <Button type="button" variant="secondary" disabled={!!mutation.current || !!archivingId} onClick={() => openEdit(record)}>{t('medicationEdit')}</Button>
          <Button type="button" variant="secondary" disabled={!!mutation.current || !!archivingId} onClick={() => { void archive(record); }}>
            {archivingId === record.id ? t('medicationArchiving') : t('medicationArchive')}
          </Button>
        </div>}
        {archiveError?.id === record.id && <p role="alert" className="recipient-error">{t(archiveError.key)}</p>}
      </li>)}
    </ul>}
    {editor && editorAllowed && <form ref={formRef} className="recipient-form medication-form"
        aria-label={t(editor.medicationId ? 'medicationEditForm' : 'medicationCreateForm')} noValidate onSubmit={(event) => { void submit(event); }}>
      {formError && <div ref={errorRef} role="alert" tabIndex={-1} className="recipient-error"><p>{t(formError)}</p></div>}
      <MedicationField field="displayName" label={t('medicationName')} value={editor.input.displayName}
        error={fieldErrors.displayName} saving={editorReadOnly} onChange={change} />
      <MedicationField field="doseText" label={t('medicationDose')} value={editor.input.doseText}
        error={fieldErrors.doseText} saving={editorReadOnly} onChange={change} />
      <MedicationField field="scheduleText" label={t('medicationSchedule')} value={editor.input.scheduleText}
        error={fieldErrors.scheduleText} saving={editorReadOnly} onChange={change} />
      <div className="recipient-actions">
        <Button type="submit" disabled={saving || (!!editor.medicationId && editedRecord?.active !== true)}>{saving ? t('medicationSaving') : t('medicationSave')}</Button>
        <Button type="button" variant="secondary" disabled={saving} onClick={closeEditor}>{t('recipientCancel')}</Button>
      </div>
      <p role="status" className="recipient-progress">{saving ? t('medicationSaving') : ''}</p>
    </form>}
  </section>;
}

function MedicationField({ field, label, value, error, saving, onChange }: {
  field: keyof MedicationInput; label: string; value: string; error?: MessageKey; saving: boolean;
  onChange: (field: keyof MedicationInput, value: string) => void;
}) {
  const { t } = useI18n();
  const id = `medication-${field}`;
  const errorId = `${id}-error`;
  const multiline = field !== 'displayName';
  return <div className="recipient-field">
    <label htmlFor={id}>{label}</label>
    {multiline
      ? <textarea id={id} name={field} dir="auto" required maxLength={500} rows={2} value={value} readOnly={saving}
          aria-invalid={!!error} aria-describedby={error ? errorId : undefined}
          onChange={(event) => onChange(field, event.target.value)} />
      : <input id={id} name={field} dir="auto" autoComplete="off" required maxLength={160} value={value} readOnly={saving}
          aria-invalid={!!error} aria-describedby={error ? errorId : undefined}
          onChange={(event) => onChange(field, event.target.value)} />}
    {error && <FieldError id={errorId} message={t(error)} />}
  </div>;
}
