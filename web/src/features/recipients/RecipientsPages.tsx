import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import type { DailyNote } from '../../api/notes';
import { recipientErrorMessage, type Recipient, type RecipientLifecycleStatus, type RecipientListFilter } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { RecipientLifecycleDialog } from './RecipientLifecycleDialog';
import { RecipientForm } from './RecipientForm';
import { relationshipLabel } from './relationshipOptions';
import { RecipientCareDetailsForm } from './RecipientCareDetailsForm';
import { MedicationListPanel } from '../medications/MedicationListPanel';
import { useRecipientResource, useRecipientsApi } from './useRecipientResource';
import { useTodayNotes } from './useTodayNotes';
import './recipients.css';

const listPath = '/app/recipients';
function RecipientLayout({ title, description, action, children }: { title: string; description: string; action?: ReactNode; children: ReactNode }) {
  const { t } = useI18n();
  return <section className="recipients-page container" aria-labelledby="recipients-heading">
    <header className="recipients-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="recipients-heading">{title}</h1><p>{description}</p></div>{action}</header>
    {children}
  </section>;
}
function RecipientStatus({ status }: { status: RecipientLifecycleStatus }) {
  const { t } = useI18n();
  const key = status === 'ACTIVE' ? 'recipientActive' : status === 'ARCHIVED' ? 'recipientArchived' : 'recipientNeedsReview';
  return <span className={`recipient-status recipient-status--${status.toLowerCase().replaceAll('_', '-')}`}>
    <span aria-hidden="true" className="status-dot" />{t(key)}
  </span>;
}
const filters: RecipientLifecycleStatus[] = ['ACTIVE', 'ARCHIVED', 'LEGACY_INACTIVE_REVIEW'];
const filterLabel = (status: RecipientLifecycleStatus): MessageKey => status === 'ACTIVE' ? 'recipientActiveFilter'
  : status === 'ARCHIVED' ? 'recipientArchivedFilter' : 'recipientReviewFilter';
const normalizeRecipientName = (name: string) => name.trim().replace(/\s+/g, ' ').toLowerCase();
const hasCareDetailValue = (value: string | null | undefined): value is string => typeof value === 'string' && value.trim().length > 0;
const hasCareDetails = (recipient: Recipient) => [
  recipient.dateOfBirth, recipient.allergies, recipient.conditions,
  recipient.emergencyContactName, recipient.emergencyContactPhone,
].some(hasCareDetailValue);
const telephoneHref = (value: string) => `tel:${value.trim().startsWith('+') ? '+' : ''}${value.replace(/\D/g, '')}`;
function localToday() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}
type TodayNoteStatus = 'recorded' | 'missing' | 'error';
function RecipientTodayStatus({ recipient, note, status, date }: { recipient: Recipient; note: DailyNote | null; status: TodayNoteStatus; date: string }) {
  const { t } = useI18n();
  const location = useLocation();
  const nameId = `recipient-card-name-${recipient.id}`;
  const actionLabelId = `recipient-card-action-${recipient.id}`;
  return <>
    <div className="recipient-note-status" role="status" aria-live="polite">
      {status === 'recorded' && <span className="recipient-note-status-label recipient-note-status-label--recorded">{t('overviewNoteRecorded')}</span>}
      {status === 'missing' && <span className="recipient-note-status-label">{t('overviewNoNote')}</span>}
      {status === 'error' && <span className="recipient-note-status-label recipient-note-status-label--error">{t('overviewNoteStatusUnavailable')}</span>}
    </div>
    {status === 'recorded' && note && <Link className="recipient-note-action" aria-labelledby={`${nameId} ${actionLabelId}`} to={`/app/notes/${note.id}`}>
      <span id={actionLabelId}>{t('overviewOpenNote')}</span>
    </Link>}
    {status === 'missing' && <Link className="recipient-note-action" aria-labelledby={`${nameId} ${actionLabelId}`}
      to={`/app/notes/new?recipientId=${encodeURIComponent(recipient.id)}&date=${encodeURIComponent(date)}`}
      state={{ backgroundLocation: location }}>
      <span id={actionLabelId}>{t('overviewRecordNote')}</span>
    </Link>}
  </>;
}
function RecipientTodayActionSection({ recipient }: { recipient: Recipient }) {
  const today = localToday();
  const todayNotes = useTodayNotes(today);
  const note = todayNotes.state === 'ready' ? todayNotes.byRecipient.get(recipient.id) ?? null : null;
  const status: TodayNoteStatus | null = todayNotes.state === 'error' ? 'error'
    : todayNotes.state === 'ready' ? (note ? 'recorded' : 'missing') : null;
  if (!status) return null;
  return <RecipientTodayAction recipient={recipient} note={note} status={status} date={today} />;
}
function RecipientTodayAction({ recipient, note, status, date }: { recipient: Recipient; note: DailyNote | null; status: TodayNoteStatus; date: string }) {
  const { t } = useI18n();
  const location = useLocation();
  return <div className="recipient-today-action">
    <div className="recipient-note-status" role="status" aria-live="polite">
      {status === 'recorded' && <span className="recipient-note-status-label recipient-note-status-label--recorded">{t('overviewNoteRecorded')}</span>}
      {status === 'missing' && <span className="recipient-note-status-label">{t('overviewNoNote')}</span>}
      {status === 'error' && <span className="recipient-note-status-label recipient-note-status-label--error">{t('overviewNoteStatusUnavailable')}</span>}
    </div>
    {status === 'recorded' && note
      ? <ButtonLink to={`/app/notes/${note.id}`}>{t('recipientOpenToday')}</ButtonLink>
      : <ButtonLink to={`/app/notes/new?recipientId=${encodeURIComponent(recipient.id)}&date=${encodeURIComponent(date)}`}
          state={{ backgroundLocation: location }}>{t('recipientRecordToday')}</ButtonLink>}
  </div>;
}

export function RecipientsListPage() {
  const { t } = useI18n(); const api = useRecipientsApi();
  const [filter, setFilter] = useState<RecipientListFilter>('ACTIVE');
  const [query, setQuery] = useState('');
  const load = useCallback((signal: AbortSignal) => api.list(signal, filter), [api, filter]);
  const { state, reload } = useRecipientResource(load);
  const today = localToday();
  const todayNotes = useTodayNotes(today);
  const empty = filter === 'ACTIVE' ? ['recipientsEmpty', 'recipientsEmptyDescription']
    : filter === 'ARCHIVED' ? ['recipientArchivedEmpty', 'recipientArchivedEmptyDescription']
      : ['recipientReviewEmpty', 'recipientReviewEmptyDescription'];
  const recipients = state.status === 'ready' ? state.data : [];
  const normalizedQuery = query.trim().toLocaleLowerCase();
  const relationshipText = (recipient: Recipient) => relationshipLabel(recipient.relationship, t);
  const matches = (recipient: Recipient) => !normalizedQuery
    || `${recipient.name}\n${recipient.relationship ?? ''}\n${relationshipText(recipient)}`.toLocaleLowerCase().includes(normalizedQuery);
  const filtered = recipients.filter(matches);
  return <RecipientLayout title={t('recipientsTitle')} description={t('recipientsDescription')}
    action={<ButtonLink to={`${listPath}/new`}>{t('recipientCreate')}<Icon name="arrow-right" className="directional-icon" /></ButtonLink>}>
    <div className="recipient-filters" role="group" aria-label={t('recipientListFilter')}>
      {filters.map((status) => <button type="button" key={status} className="recipient-filter" aria-pressed={filter === status}
        onClick={() => setFilter(status)}>{t(filterLabel(status))}</button>)}
    </div>
    {state.status === 'loading' && <LoadingState label={t('recipientsLoading')} />}
    {state.status === 'error' && <ErrorState description={t(recipientErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && state.data.length > 0 && <div className="recipient-search">
      <label htmlFor="recipient-search">{t('recipientSearch')}</label>
      <div><input id="recipient-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} />
        <Button type="button" variant="secondary" disabled={!query} onClick={() => setQuery('')}>{t('recipientSearchClear')}</Button></div>
    </div>}
    {state.status === 'ready' && state.data.length === 0 && <EmptyState title={t(empty[0] as MessageKey)} description={t(empty[1] as MessageKey)} />}
    {state.status === 'ready' && state.data.length > 0 && filtered.length === 0
      && <EmptyState title={t('recipientSearchNoResults')} description={t('recipientSearchNoResultsDescription')} />}
    {filtered.length > 0 && <ul className="recipient-list" aria-label={t(filterLabel(filter as RecipientLifecycleStatus))}>
      {filtered.map((recipient) => {
        const note = recipient.lifecycleStatus === 'ACTIVE' && todayNotes.state === 'ready'
          ? todayNotes.byRecipient.get(recipient.id) ?? null : null;
        const noteStatus: TodayNoteStatus | null = recipient.lifecycleStatus !== 'ACTIVE' ? null
          : todayNotes.state === 'error' ? 'error'
          : todayNotes.state === 'ready' ? (note ? 'recorded' : 'missing') : null;
        return <li key={recipient.id} className="recipient-card">
          <span className="person-avatar"><Icon name="people" size={24} /></span>
          <RecipientStatus status={recipient.lifecycleStatus} />
          <h2><Link to={`${listPath}/${recipient.id}`}><bdi id={`recipient-card-name-${recipient.id}`}>{recipient.name}</bdi></Link></h2>
            <p className="recipient-hint">{t('recipientRelationshipLabel')}</p>
            <p><bdi>{relationshipText(recipient) || t('recipientRelationshipEmpty')}</bdi></p>
            {noteStatus && <RecipientTodayStatus recipient={recipient} note={note} status={noteStatus} date={today} />}
          </li>;
      })}
      </ul>}
  </RecipientLayout>;
}
export function CreateRecipientPage() {
  const { t } = useI18n(); const api = useRecipientsApi(); const navigate = useNavigate();
  return <RecipientLayout title={t('recipientCreate')} description={t('recipientCreateDescription')}>
    <div className="recipient-surface"><RecipientForm cancelTo={listPath}
      onCheckDuplicate={async (input, signal) => {
        const name = normalizeRecipientName(input.name);
        const recipients = await api.list(signal, 'ALL');
        return recipients.find((recipient) => normalizeRecipientName(recipient.name) === name) ?? null;
      }}
      onSave={async (input, key, signal) => {
        const recipient = await api.create(input, key, signal);
        if (!signal.aborted) navigate(`${listPath}/${recipient.id}`, { replace: true, state: { recipientNotice: 'created' } });
      }} /></div>
  </RecipientLayout>;
}
function RecipientRecord({ id, edit }: { id: string; edit: boolean }) {
  const { t, language } = useI18n(); const api = useRecipientsApi(); const navigate = useNavigate(); const location = useLocation();
  const load = useCallback((signal: AbortSignal) => api.get(id, signal), [api, id]);
  const { state, reload, replace } = useRecipientResource<Recipient>(load);
  const [confirming, setConfirming] = useState<'archive' | 'restore' | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(() => {
    const value = (location.state as { recipientNotice?: unknown } | null)?.recipientNotice;
    return value === 'created' ? 'recipientCreated' : value === 'updated' ? 'recipientUpdated' : null;
  });
  const noticeRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => { if (notice && state.status === 'ready') noticeRef.current?.focus(); }, [notice, state.status]);
  const date = (value: string, timeZone?: Intl.DateTimeFormatOptions['timeZone']) =>
    new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', {
      dateStyle: 'medium', ...(timeZone ? { timeZone } : {}),
    }).format(new Date(value));
  return <RecipientLayout title={t(edit ? 'recipientEdit' : 'recipientDetails')} description={t(edit ? 'recipientEditDescription' : 'recipientDetailsDescription')}
    action={<ButtonLink variant="secondary" to={listPath}><Icon name="arrow-left" className="directional-icon" />{t('recipientsBack')}</ButtonLink>}>
    {state.status === 'loading' && <LoadingState label={t('recipientLoading')} />}
    {state.status === 'error' && <ErrorState description={t(recipientErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && <>
      {notice && <p ref={noticeRef} tabIndex={-1} role="status" className="recipient-success">{t(notice)}</p>}
      <div className="recipient-surface">
        <div className="recipient-record-heading"><h2><bdi>{state.data.name}</bdi></h2><RecipientStatus status={state.data.lifecycleStatus} /></div>
        {state.data.lifecycleStatus === 'ARCHIVED' && <p className="recipient-inactive-notice">{t('recipientArchivedDescription')}</p>}
        {state.data.lifecycleStatus === 'LEGACY_INACTIVE_REVIEW' && <p className="recipient-review-notice">{t('recipientLegacyReviewDescription')}</p>}
        {!edit && state.data.lifecycleStatus === 'ACTIVE' && <RecipientTodayActionSection recipient={state.data} />}
        {edit && state.data.lifecycleStatus === 'ACTIVE' ? <>
          <RecipientForm initial={state.data} cancelTo={`${listPath}/${id}`} onReload={reload} onSave={async (input, _key, signal) => {
            await api.update(id, input, signal);
            if (!signal.aborted) navigate(`${listPath}/${id}`, { replace: true, state: { recipientNotice: 'updated' } });
          }} />
          <RecipientCareDetailsForm initial={state.data} onSave={(input, signal) => api.updateCareDetails(id, input, signal)} />
        </> : <>
          <dl className="recipient-details">
            <div><dt>{t('recipientRelationshipLabel')}</dt><dd><bdi>{relationshipLabel(state.data.relationship, t) || t('recipientRelationshipEmpty')}</bdi></dd></div>
            <div><dt>{t('recipientCreatedAt')}</dt><dd><time dateTime={state.data.createdAt}>{date(state.data.createdAt)}</time></dd></div>
            <div><dt>{t('recipientUpdatedAt')}</dt><dd><time dateTime={state.data.updatedAt}>{date(state.data.updatedAt)}</time></dd></div>
            {state.data.archivedAt && <div><dt>{t('recipientArchivedAt')}</dt><dd><time dateTime={state.data.archivedAt}>{date(state.data.archivedAt)}</time></dd></div>}
            {state.data.restoredAt && <div><dt>{t('recipientRestoredAt')}</dt><dd><time dateTime={state.data.restoredAt}>{date(state.data.restoredAt)}</time></dd></div>}
          </dl>
          {hasCareDetails(state.data) && <section className="recipient-care-details-overview" aria-labelledby="care-details-section-title">
            <div className="recipient-care-details-overview-heading">
              <h3 id="care-details-section-title">{t('careDetailsSectionTitle')}</h3>
              {state.data.lifecycleStatus === 'ACTIVE' && <ButtonLink variant="secondary" to={`${listPath}/${encodeURIComponent(id)}/edit`}>{t('careDetailsEdit')}</ButtonLink>}
            </div>
            <dl className="recipient-details recipient-care-details-list">
              {hasCareDetailValue(state.data.dateOfBirth) && <div><dt>{t('careDetailsDateOfBirth')}</dt><dd><time dateTime={state.data.dateOfBirth}>{date(state.data.dateOfBirth, 'UTC')}</time></dd></div>}
              {hasCareDetailValue(state.data.allergies) && <div><dt>{t('careDetailsAllergies')}</dt><dd className="recipient-care-details-text" dir="auto">{state.data.allergies}</dd></div>}
              {hasCareDetailValue(state.data.conditions) && <div><dt>{t('careDetailsConditions')}</dt><dd className="recipient-care-details-text" dir="auto">{state.data.conditions}</dd></div>}
              {hasCareDetailValue(state.data.emergencyContactName) && <div><dt>{t('careDetailsEmergencyContactName')}</dt><dd className="recipient-care-details-text" dir="auto">{state.data.emergencyContactName}</dd></div>}
              {hasCareDetailValue(state.data.emergencyContactPhone) && <div><dt>{t('careDetailsEmergencyContactPhone')}</dt><dd><a href={telephoneHref(state.data.emergencyContactPhone)} dir="ltr">{state.data.emergencyContactPhone}</a></dd></div>}
            </dl>
          </section>}
          {state.data.lifecycleStatus === 'ACTIVE' && <div className="recipient-actions">
            <ButtonLink variant="secondary" to={`${listPath}/${id}/edit`}>{t('recipientEdit')}</ButtonLink>
          </div>}
          {edit && state.data.lifecycleStatus !== 'ACTIVE' && <ButtonLink variant="secondary" to={`${listPath}/${id}`}>{t('recipientDetails')}</ButtonLink>}
        </>}
      </div>
      {!edit && <div className="recipient-surface medication-surface">
        <MedicationListPanel recipientId={state.data.id} canEdit={state.data.lifecycleStatus === 'ACTIVE'} />
      </div>}
      {!edit && (state.data.lifecycleStatus === 'ACTIVE' || state.data.lifecycleStatus === 'ARCHIVED') && <section className="recipient-manage" aria-labelledby="recipient-manage-title">
        <h2 id="recipient-manage-title">{t('recipientManageTitle')}</h2>
        <div className="recipient-actions">
          {state.data.lifecycleStatus === 'ACTIVE' && <Button variant="secondary" onClick={() => { setNotice(null); setConfirming('archive'); }}>{t('recipientArchive')}</Button>}
          {state.data.lifecycleStatus === 'ARCHIVED' && <Button variant="secondary" onClick={() => { setNotice(null); setConfirming('restore'); }}>{t('recipientRestore')}</Button>}
        </div>
      </section>}
      {confirming && <RecipientLifecycleDialog recipient={state.data} action={confirming} onClose={() => setConfirming(null)} onConfirm={async (signal) => {
        const updated = confirming === 'archive' ? await api.archive(id, signal) : await api.restore(id, signal);
        if (!signal.aborted) { replace(updated); setNotice(confirming === 'archive' ? 'recipientArchivedSuccess' : 'recipientRestoredSuccess'); }
      }} />}
    </>}
  </RecipientLayout>;
}
export function RecipientPage({ edit = false }: { edit?: boolean }) {
  const { id = '' } = useParams();
  return <RecipientRecord key={`${id}:${edit}`} id={id} edit={edit} />;
}
export function RecipientsNotFoundPage() {
  const { t } = useI18n();
  return <RecipientLayout title={t('pageNotFound')} description={t('recipientUnavailable')}><ButtonLink variant="secondary" to={listPath}>{t('recipientsBack')}</ButtonLink></RecipientLayout>;
}
