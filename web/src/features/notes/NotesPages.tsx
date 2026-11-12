import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { noteErrorMessage, isNoteDate, type AddendumInput, type CorrectionEvent, type CreateNoteInput, type FieldCorrection, type NoteAddendum } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { NoteForm } from './NoteForm';
import { ObservationValues } from './ObservationValues';
import { CorrectionEditor } from './CorrectionEditor';
import { NoteTimelineRow } from './NoteTimelineRow';
import { CorrectionEffectiveValues, CorrectionEventRow } from './CorrectionHistory';
import { fieldLabels } from './noteForm';
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
  const formKeyParams = new URLSearchParams(params);
  formKeyParams.delete('date');
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
      : <div className="note-surface"><NoteForm key={formKeyParams.toString()} mode="create" recipients={state.data} initialRecipientId={initialRecipientId} initialDate={initialDate} cancelTo={cancelTo} onSave={async (input, key, signal) => {
        const note = await api.notes.create(input as CreateNoteInput, key, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`, { replace: true, state: { noteNotice: 'created' } });
      }} onOpen={async (recipientId, date, signal) => {
        const note = await api.notes.find(recipientId, date, signal);
        if (!signal.aborted) navigate(`${notesPath}/${note.id}`);
      }} /></div>)}
  </NotesLayout>;
}

type TimelineEntry =
  | { kind: 'addendum'; id: string; createdAt: string; entry: NoteAddendum }
  | { kind: 'correction'; id: string; createdAt: string; sequenceNo: number; event: CorrectionEvent };

function orderTimeline(addenda: NoteAddendum[], events: CorrectionEvent[]): TimelineEntry[] {
  const orderedAddenda = [...addenda].sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt)
    || (left.id < right.id ? -1 : left.id > right.id ? 1 : 0));
  const timeline: TimelineEntry[] = [
    ...orderedAddenda.map((entry) => ({ kind: 'addendum' as const, id: entry.id, createdAt: entry.createdAt, entry })),
    ...events.map((event) => ({
      kind: 'correction' as const, id: event.id, createdAt: event.createdAt, sequenceNo: event.sequenceNo, event,
    })),
  ];
  return timeline.sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt)
    || (left.kind === 'correction' && right.kind === 'correction'
      ? left.sequenceNo - right.sequenceNo
      : left.kind !== right.kind ? (left.kind === 'correction' ? -1 : 1)
        : left.kind === 'addendum' && right.kind === 'addendum'
          ? (left.id < right.id ? -1 : left.id > right.id ? 1 : 0)
          : 0));
}
function NoteChangesTimeline({
  id, recipientActive, lifecycleStatus, editing, onEdit, onCancel, onSaved, date,
}: {
  id: string;
  recipientActive: boolean;
  lifecycleStatus: 'ACTIVE' | 'ARCHIVED' | 'LEGACY_INACTIVE_REVIEW';
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onSaved: () => void;
  date: (value: string, instant?: boolean) => string;
}) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const loadAddenda = useCallback((signal: AbortSignal) => api.notes.addenda(id, signal), [api, id]);
  const loadCorrections = useCallback((signal: AbortSignal) => api.notes.correctionHistory(id, signal), [api, id]);
  const { state: addendaState, reload: reloadAddenda } = useNoteResource(loadAddenda);
  const { state: correctionState, reload: reloadCorrections } = useNoteResource(loadCorrections);
  const addenda = addendaState.status === 'ready' ? addendaState.data : [];
  const events = correctionState.status === 'ready' ? correctionState.data.events : [];
  const timeline = orderTimeline(addenda, events);
  const addendumNumber = new Map(timeline.filter((item): item is Extract<TimelineEntry, { kind: 'addendum' }> => item.kind === 'addendum')
    .map((item, index) => [item.id, index + 1]));
  const titleId = `note-changes-title-${id}`;

  return <section className="note-surface note-structured-corrections" aria-labelledby={titleId}>
    <div className="note-actions"><h2 id={titleId}>{t('notesChanges')}</h2>
      {recipientActive && !editing && <Button onClick={onEdit}>{t('notesAddStructuredCorrection')}</Button>}
      {!recipientActive && <p className="note-hint">{t(lifecycleStatus === 'ARCHIVED' ? 'recipientArchivedReadonly' : 'recipientLegacyReviewDescription')}</p>}
      {recipientActive && <ButtonLink to={`${notesPath}/${id}/addenda/new`}>{t('notesAppend')}</ButtonLink>}
    </div>
    {addendaState.status === 'loading' && <LoadingState label={t('notesLoading')} />}
    {addendaState.status === 'error' && <ErrorState description={t(noteErrorMessage(addendaState.error))} onRetry={reloadAddenda} />}
    {correctionState.status === 'loading' && <LoadingState label={t('notesLoading')} />}
    {correctionState.status === 'error' && <ErrorState description={t(noteErrorMessage(correctionState.error))} onRetry={reloadCorrections} />}
    {correctionState.status === 'ready' && correctionState.data.events.length > 0
      && <CorrectionEffectiveValues history={correctionState.data} idPrefix={id} />}
    {addendaState.status === 'ready' && addenda.length === 0 && <p className="note-hint">{t('notesNoAddenda')}</p>}
    {correctionState.status === 'ready' && correctionState.data.events.length === 0
      && <p className="note-hint">{t('notesNoStructuredCorrections')}</p>}
    {editing && <CorrectionEditor onCancel={onCancel} onSave={async (entries: FieldCorrection[], key, signal) => {
      await api.notes.correct(id, entries, key, signal);
      if (!signal.aborted) {
        reloadCorrections();
        onSaved();
      }
    }} />}
    {timeline.length > 0 && <ol className="note-addenda-list">{timeline.map((item) => (
      item.kind === 'addendum'
        ? <li key={`addendum:${item.id}`}>
          <NoteTimelineRow
            entryLabel={<>{t('notesAppend')} <bdi>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(addendumNumber.get(item.id) ?? 0)}</bdi></>}
            dateLabel={date(item.createdAt, true)}
            dateTime={item.createdAt}
            provenance={<>{t('notesTimelineSourceId')}: <bdi>{item.id}</bdi></>}
            body={<ObservationValues values={item.entry.observations} providedFields={item.entry.providedFields}
              verificationProvided={item.entry.medicationVerificationProvided} correction />}
          />
        </li>
        : <li key={`correction:${item.id}`}>
          <CorrectionEventRow event={item.event} idPrefix={id}
            entryLabel={<>{t('notesCorrectionEvent')} <bdi>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(item.event.sequenceNo)}</bdi> · {t(fieldLabels[item.event.field])}</>} />
        </li>
    ))}</ol>}
    {timeline.length === 0 && addendaState.status === 'ready' && correctionState.status === 'ready'
      && <p className="note-hint">{t('notesNoChanges')}</p>}
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
          provenance={<>{t('notesRecordedAt')}: <time dateTime={state.data.note.createdAt} dir="auto">{date(state.data.note.createdAt, true)}</time> · {t('notesTimelineSourceId')}: <bdi>{state.data.note.id}</bdi></>}
          body={<ObservationValues values={state.data.note.observations} />}
        />
      </section>
      {append && !state.data.recipient.active
        ? <p className="note-notice" role="status">{t(state.data.recipient.lifecycleStatus === 'ARCHIVED' ? 'recipientArchivedReadonly' : 'recipientLegacyReviewDescription')}</p>
        : append ? <div className="note-surface"><NoteForm mode="append" cancelTo={`${notesPath}/${id}`} onSave={async (input, key, signal) => {
          await api.notes.append(id, input as AddendumInput, key, signal);
          if (!signal.aborted) navigate(`${notesPath}/${id}`, { replace: true, state: { noteNotice: 'appended' } });
        }} /></div> : <>
      <NoteChangesTimeline
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
        date={date}
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
