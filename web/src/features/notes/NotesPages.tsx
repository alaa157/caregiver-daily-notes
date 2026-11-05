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
import { NoteTimelineRow } from './NoteTimelineRow';
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
  const load = useCallback((signal: AbortSignal) => api.recipients.list(signal, 'ACTIVE'), [api]);
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

function CorrectionSection({
  id, recipientActive, lifecycleStatus, editing, onEdit, onCancel, onSaved,
}: {
  id: string;
  recipientActive: boolean;
  lifecycleStatus: 'ACTIVE' | 'ARCHIVED' | 'LEGACY_INACTIVE_REVIEW';
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const { t } = useI18n();
  const api = useNotesApis();
  const load = useCallback((signal: AbortSignal) => api.notes.correctionHistory(id, signal), [api, id]);
  const { state, reload } = useNoteResource(load);
  return <section className="note-surface note-structured-corrections" aria-labelledby={`structured-correction-title-${id}`}>
    <div className="note-actions"><h2 id={`structured-correction-title-${id}`}>{t('notesStructuredCorrections')}</h2>
      {recipientActive && !editing && <Button onClick={onEdit}>{t('notesAddStructuredCorrection')}</Button>}
      {!recipientActive && <p className="note-hint">{t(lifecycleStatus === 'ARCHIVED' ? 'recipientArchivedReadonly' : 'recipientLegacyReviewDescription')}</p>}
    </div>
    {editing ? <CorrectionEditor onCancel={onCancel} onSave={async (entries: FieldCorrection[], key, signal) => {
      await api.notes.correct(id, entries, key, signal);
      if (!signal.aborted) reload();
      if (!signal.aborted) onSaved();
    }} /> : state.status === 'loading'
      ? <LoadingState label={t('notesLoading')} />
      : state.status === 'error'
        ? <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />
        : <CorrectionHistory history={state.data} idPrefix={id} />}
  </section>;
}


function AddendaSection({
  id, recipientActive, lifecycleStatus, date,
}: {
  id: string;
  recipientActive: boolean;
  lifecycleStatus: 'ACTIVE' | 'ARCHIVED' | 'LEGACY_INACTIVE_REVIEW';
  date: (value: string, instant?: boolean) => string;
}) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const load = useCallback((signal: AbortSignal) => api.notes.addenda(id, signal), [api, id]);
  const { state, reload } = useNoteResource(load);
  const orderedAddenda = state.status === 'ready'
    ? [...state.data].sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt) || left.id.localeCompare(right.id))
    : [];
  return <section className="note-addenda" aria-labelledby={`note-addenda-title-${id}`}>
    <div className="note-actions"><h2 id={`note-addenda-title-${id}`}>{t('notesAddenda')}</h2>
      {recipientActive ? <ButtonLink to={`${notesPath}/${id}/addenda/new`}>{t('notesAppend')}</ButtonLink>
        : <p className="note-hint">{t(lifecycleStatus === 'ARCHIVED' ? 'recipientArchivedReadonly' : 'recipientLegacyReviewDescription')}</p>}
    </div>
    {state.status === 'loading' ? <LoadingState label={t('notesLoading')} />
      : state.status === 'error' ? <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />
        : orderedAddenda.length === 0 ? <p className="note-surface">{t('notesNoAddenda')}</p>
          : <ol className="note-addenda-list">{orderedAddenda.map((item, index) => <li key={item.id}>
            <NoteTimelineRow
              entryLabel={`${t('notesAppend')} ${new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(index + 1)}`}
              dateLabel={date(item.createdAt, true)}
              dateTime={item.createdAt}
              provenance={<><span>{t('notesTimelineSourceId')}:</span> <bdi>{item.id}</bdi></>}
              body={<ObservationValues values={item.observations} providedFields={item.providedFields}
                verificationProvided={item.medicationVerificationProvided} correction />}
            />
          </li>)}</ol>}
  </section>;
}

function NoteRecord({ id, append }: { id: string; append: boolean }) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const navigate = useNavigate();
  const location = useLocation();
  const [editingCorrection, setEditingCorrection] = useState(false);
  const load = useCallback(async (signal: AbortSignal) => {
    const note = await api.notes.get(id, signal);
    const recipient = await api.recipients.get(note.recipientId, signal);
    return { note, recipient };
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
      <section className="note-timeline" aria-label={t('notesImmutable')}>
        <NoteTimelineRow
          entryLabel={t('notesImmutable')}
          dateLabel={date(state.data.note.date)}
          dateTime={state.data.note.date}
          metadata={<Link to={`/app/recipients/${encodeURIComponent(state.data.recipient.id)}`}><bdi>{state.data.recipient.name}</bdi></Link>}
          provenance={<>{t('notesRecordedAt')}: <time dateTime={state.data.note.createdAt}>{date(state.data.note.createdAt, true)}</time> · {t('notesTimelineSourceId')}: <bdi>{state.data.note.id}</bdi></>}
          body={<ObservationValues values={state.data.note.observations} />}
        />
      </section>
      {append && !state.data.recipient.active
        ? <p className="note-notice" role="status">{t(state.data.recipient.lifecycleStatus === 'ARCHIVED' ? 'recipientArchivedReadonly' : 'recipientLegacyReviewDescription')}</p>
        : append ? <div className="note-surface"><NoteForm mode="append" cancelTo={`${notesPath}/${id}`} onSave={async (input, key, signal) => {
          await api.notes.append(id, input as AddendumInput, key, signal);
          if (!signal.aborted) navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'appended' } });
        }} /></div> : <>
      <AddendaSection id={id} recipientActive={state.data.recipient.active} lifecycleStatus={state.data.recipient.lifecycleStatus} date={date} />
      <CorrectionSection
        id={id}
        recipientActive={state.data.recipient.active}
        lifecycleStatus={state.data.recipient.lifecycleStatus}
        editing={editingCorrection}
        onEdit={() => setEditingCorrection(true)}
        onCancel={() => setEditingCorrection(false)}
        onSaved={() => {
          setEditingCorrection(false);
          navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'correctionSaved' } });
        }}
      />
      </>}
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
