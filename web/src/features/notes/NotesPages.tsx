import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { noteErrorMessage, isNoteDate, type AddendumInput, type CreateNoteInput, type FieldCorrection } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { NoteForm } from './NoteForm';
import { ObservationValues } from './ObservationValues';
import { CorrectionEditor } from './CorrectionEditor';
import { CorrectionHistory } from './CorrectionHistory';
import { useNoteResource, useNotesApis } from './useNotes';
import './notes.css';

export const notesPath = '/app/notes';

function NotesLayout({ title, description, children, action }: { title: string; description: string; children: ReactNode; action?: ReactNode }) {
  const { t } = useI18n();
  return <section className="notes-page container" aria-labelledby="notes-heading">
    <header className="notes-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="notes-heading">{title}</h1><p>{description}</p></div>{action}</header>
    <p className="notes-history-link"><ButtonLink variant="text" to={notesPath}>{t('historyTitle')}</ButtonLink></p>
    {children}
  </section>;
}

export function CreateNotePage() {
  const { t } = useI18n();
  const api = useNotesApis();
  const navigate = useNavigate();
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const requestedRecipient = params.get('recipientId');
  const initialRecipientId = requestedRecipient && isRecipientId(requestedRecipient) ? requestedRecipient : undefined;
  const requestedDate = params.get('date');
  const initialDate = requestedDate && isNoteDate(requestedDate) ? requestedDate : undefined;
  const load = useCallback((signal: AbortSignal) => api.recipients.list(signal), [api]);
  const { state, reload } = useNoteResource(load);
  const cancelTo = initialRecipientId && state.status === 'ready' && state.data.some((recipient) => recipient.id === initialRecipientId && recipient.active)
    ? `/app/recipients/${encodeURIComponent(initialRecipientId)}` : '/app';
  return <NotesLayout title={t('notesCreate')} description={t('notesCreateDescription')}>
    {state.status === 'loading' && <LoadingState label={t('notesLoading')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && (state.data.length === 0 ? <div className="note-surface"><EmptyState title={t('notesNoRecipients')} description={t('notesNoRecipientsDescription')} />
      <ButtonLink to="/app/recipients">{t('recipientsTitle')}</ButtonLink></div>
      : <div className="note-surface"><NoteForm key={location.search} mode="create" recipients={state.data} initialRecipientId={initialRecipientId} initialDate={initialDate} cancelTo={cancelTo} onSave={async (input, key, signal) => {
        const note = await api.notes.create(input as CreateNoteInput, key, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`, { replace: true, state: { noteNotice: 'created' } });
      }} onOpen={async (recipientId, date, signal) => {
        const note = await api.notes.find(recipientId, date, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`);
      }} /></div>)}
  </NotesLayout>;
}

function NoteRecord({ id, append }: { id: string; append: boolean }) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const navigate = useNavigate();
  const location = useLocation();
  const [editingCorrection, setEditingCorrection] = useState(false);
  const load = useCallback(async (signal: AbortSignal) => {
    const note = await api.notes.get(id, signal);
    const [addenda, recipient, corrections] = await Promise.all([api.notes.addenda(id, signal), api.recipients.get(note.recipientId, signal), api.notes.correctionHistory(id, signal)]);
    return { note, addenda, recipient, corrections };
  }, [api, id]);
  const { state, reload } = useNoteResource(load);
  const notice = (location.state as { noteNotice?: unknown } | null)?.noteNotice;
  const noticeKey = notice === 'created' ? 'notesSaved' : notice === 'appended' ? 'notesAppendSaved' : notice === 'correctionSaved' ? 'notesCorrectionSaved' : null;
  const noticeRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => { if (state.status === 'ready' && noticeKey) noticeRef.current?.focus(); }, [state.status, noticeKey]);
  const date = (value: string, instant = false) => new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en',
    { dateStyle: 'medium', ...(instant ? { timeStyle: 'short' as const } : {}) }).format(new Date(instant ? value : `${value}T12:00:00`));
  return <NotesLayout title={t(append ? 'notesAppend' : 'notesDetails')} description={t(append ? 'notesAppendHint' : 'notesImmutableDescription')}
    action={<ButtonLink variant="secondary" to={append ? `${notesPath}/${id}` : `${notesPath}/new`}>{t(append ? 'notesBack' : 'notesNew')}</ButtonLink>}>
    {state.status === 'loading' && <LoadingState label={t('notesLoading')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && <>
      {!append && noticeKey && <p ref={noticeRef} tabIndex={-1} role="status" className="note-success">{t(noticeKey)}</p>}
      <article className="note-surface" aria-labelledby="original-note-title">
        <p className="note-recipient"><Link to={`/app/recipients/${encodeURIComponent(state.data.recipient.id)}`}><bdi>{state.data.recipient.name}</bdi></Link> · <time dateTime={state.data.note.date}>{date(state.data.note.date)}</time></p>
        <h2 id="original-note-title">{t('notesImmutable')}</h2>
        <p className="note-hint">{t('notesRecordedAt')}: <time dateTime={state.data.note.createdAt}>{date(state.data.note.createdAt, true)}</time></p>
        <ObservationValues values={state.data.note.observations} />
      </article>
      {append ? <div className="note-surface"><NoteForm mode="append" cancelTo={`${notesPath}/${id}`} onSave={async (input, key, signal) => {
        await api.notes.append(id, input as AddendumInput, key, signal);
        if (!signal.aborted) navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'appended' } });
      }} /></div> : <>
      <section className="note-surface note-structured-corrections" aria-labelledby="structured-correction-title">
        <div className="note-actions"><h2 id="structured-correction-title">{t('notesStructuredCorrections')}</h2>
          {!editingCorrection && <Button onClick={() => setEditingCorrection(true)}>{t('notesAddStructuredCorrection')}</Button>}
        </div>
        {editingCorrection ? <CorrectionEditor onCancel={() => setEditingCorrection(false)} onSave={async (entries: FieldCorrection[], key, signal) => {
          await api.notes.correct(id, entries, key, signal);
          if (!signal.aborted) { setEditingCorrection(false); reload(); navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'correctionSaved' } }); }
        }} /> : <CorrectionHistory history={state.data.corrections} />}
      </section>
      <section className="note-addenda" aria-labelledby="note-addenda-title">
        <div className="note-actions"><h2 id="note-addenda-title">{t('notesAddenda')}</h2><ButtonLink to={`${notesPath}/${id}/addenda/new`}>{t('notesAppend')}</ButtonLink></div>
        {state.data.addenda.length === 0 ? <p className="note-surface">{t('notesNoAddenda')}</p> : <ol className="note-addenda-list">{state.data.addenda.map((item, index) => <li key={item.id}>
          <article className="note-surface" aria-labelledby={`addendum-${item.id}`}><h3 id={`addendum-${item.id}`}>{t('notesAppend')} {new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(index + 1)}</h3>
            <p className="note-hint"><time dateTime={item.createdAt}>{date(item.createdAt, true)}</time></p>
            <ObservationValues values={item.observations} providedFields={item.providedFields}
              verificationProvided={item.medicationVerificationProvided} correction /></article>
        </li>)}</ol>}
      </section></>}
    </>}
  </NotesLayout>;
}

export function NotePage({ append = false }: { append?: boolean }) {
  const { id = '' } = useParams();
  return <NoteRecord key={`${id}:${append}`} id={id} append={append} />;
}

export function NotesNotFoundPage() {
  const { t } = useI18n();
  return <NotesLayout title={t('pageNotFound')} description={t('notesUnavailable')}><ButtonLink to={`${notesPath}/new`}>{t('notesNew')}</ButtonLink></NotesLayout>;
}
