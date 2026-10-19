import { useCallback, useEffect, useRef, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { noteErrorMessage, observationFields, type AddendumInput, type CreateNoteInput, type Observations } from '../../api/notes';
import { ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { NoteForm } from './NoteForm';
import { fieldLabels } from './noteForm';
import { useNoteResource, useNotesApis } from './useNotes';
import './notes.css';

export const notesPath = '/app/notes';

function NotesLayout({ title, description, children, action }: { title: string; description: string; children: ReactNode; action?: ReactNode }) {
  const { t } = useI18n();
  return <section className="notes-page container" aria-labelledby="notes-heading">
    <header className="notes-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="notes-heading">{title}</h1><p>{description}</p></div>{action}</header>
    {children}
  </section>;
}

export function CreateNotePage() {
  const { t } = useI18n();
  const api = useNotesApis();
  const navigate = useNavigate();
  const load = useCallback((signal: AbortSignal) => api.recipients.list(signal), [api]);
  const { state, reload } = useNoteResource(load);
  return <NotesLayout title={t('notesCreate')} description={t('notesCreateDescription')}>
    {state.status === 'loading' && <LoadingState label={t('notesLoading')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && (state.data.length === 0 ? <div className="note-surface"><EmptyState title={t('notesNoRecipients')} description={t('notesNoRecipientsDescription')} />
      <ButtonLink to="/app/recipients">{t('recipientsTitle')}</ButtonLink></div>
      : <div className="note-surface"><NoteForm mode="create" recipients={state.data} cancelTo="/app" onSave={async (input, key, signal) => {
        const note = await api.notes.create(input as CreateNoteInput, key, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`, { replace: true, state: { noteNotice: 'created' } });
      }} onOpen={async (recipientId, date, signal) => {
        const note = await api.notes.find(recipientId, date, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`);
      }} /></div>)}
  </NotesLayout>;
}

function ObservationValues({ values, correction = false }: { values: Observations; correction?: boolean }) {
  const { t, language } = useI18n();
  function renderValue(field: keyof Observations) {
    const value = values[field];
    if (value === null) return t('notesNotRecorded');
    if (value === '') return t('notesEmptyValue');
    if (typeof value === 'boolean') return t(value ? 'notesYes' : 'notesNo');
    if (field === 'medicationStatus') return t(value === 'TAKEN' ? 'notesTaken' : value === 'MISSED' ? 'notesMissed' : 'notesUnknown');
    if (typeof value === 'number') return new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
    return value;
  }
  return <dl className="note-values">{observationFields.filter((field) => !correction || values[field] !== null).map((field) => <div key={field} className={field === 'freeText' ? 'note-field--wide' : ''}>
    <dt>{t(fieldLabels[field])}</dt><dd dir="auto" className="note-verbatim">{renderValue(field)}</dd>
  </div>)}</dl>;
}

function NoteRecord({ id, append }: { id: string; append: boolean }) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const navigate = useNavigate();
  const location = useLocation();
  const load = useCallback(async (signal: AbortSignal) => {
    const note = await api.notes.get(id, signal);
    const [addenda, recipient] = await Promise.all([api.notes.addenda(id, signal), api.recipients.get(note.recipientId, signal)]);
    return { note, addenda, recipient };
  }, [api, id]);
  const { state, reload } = useNoteResource(load);
  const notice = (location.state as { noteNotice?: unknown } | null)?.noteNotice;
  const noticeKey = notice === 'created' ? 'notesSaved' : notice === 'appended' ? 'notesAppendSaved' : null;
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
        <p className="note-recipient"><bdi>{state.data.recipient.name}</bdi> · <time dateTime={state.data.note.date}>{date(state.data.note.date)}</time></p>
        <h2 id="original-note-title">{t('notesImmutable')}</h2>
        <p className="note-hint">{t('notesRecordedAt')}: <time dateTime={state.data.note.createdAt}>{date(state.data.note.createdAt, true)}</time></p>
        <ObservationValues values={state.data.note} />
      </article>
      {append ? <div className="note-surface"><NoteForm mode="append" cancelTo={`${notesPath}/${id}`} onSave={async (input, key, signal) => {
        await api.notes.append(id, input as AddendumInput, key, signal);
        if (!signal.aborted) navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'appended' } });
      }} /></div> : <section className="note-addenda" aria-labelledby="note-addenda-title">
        <div className="note-actions"><h2 id="note-addenda-title">{t('notesAddenda')}</h2><ButtonLink to={`${notesPath}/${id}/addenda/new`}>{t('notesAppend')}</ButtonLink></div>
        {state.data.addenda.length === 0 ? <p className="note-surface">{t('notesNoAddenda')}</p> : <ol className="note-addenda-list">{state.data.addenda.map((item, index) => <li key={item.id}>
          <article className="note-surface" aria-labelledby={`addendum-${item.id}`}><h3 id={`addendum-${item.id}`}>{t('notesAppend')} {new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(index + 1)}</h3>
            <p className="note-hint"><time dateTime={item.createdAt}>{date(item.createdAt, true)}</time></p><ObservationValues values={item} correction /></article>
        </li>)}</ol>}
      </section>}
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
