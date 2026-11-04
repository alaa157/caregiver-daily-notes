import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { recipientErrorMessage, type Recipient, type RecipientLifecycleStatus, type RecipientListFilter } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { RecipientLifecycleDialog } from './RecipientLifecycleDialog';
import { RecipientForm } from './RecipientForm';
import { useRecipientResource, useRecipientsApi } from './useRecipientResource';
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

export function RecipientsListPage() {
  const { t } = useI18n(); const api = useRecipientsApi();
  const [filter, setFilter] = useState<RecipientListFilter>('ACTIVE');
  const load = useCallback((signal: AbortSignal) => api.list(signal, filter), [api, filter]);
  const { state, reload } = useRecipientResource(load);
  const empty = filter === 'ACTIVE' ? ['recipientsEmpty', 'recipientsEmptyDescription']
    : filter === 'ARCHIVED' ? ['recipientArchivedEmpty', 'recipientArchivedEmptyDescription']
      : ['recipientReviewEmpty', 'recipientReviewEmptyDescription'];
  return <RecipientLayout title={t('recipientsTitle')} description={t('recipientsDescription')}
    action={<ButtonLink to={`${listPath}/new`}>{t('recipientCreate')}<Icon name="arrow-right" className="directional-icon" /></ButtonLink>}>
    <div className="recipient-filters" role="group" aria-label={t('recipientListFilter')}>
      {filters.map((status) => <button type="button" key={status} className="recipient-filter" aria-pressed={filter === status}
        onClick={() => setFilter(status)}>{t(filterLabel(status))}</button>)}
    </div>
    {state.status === 'loading' && <LoadingState label={t('recipientsLoading')} />}
    {state.status === 'error' && <ErrorState description={t(recipientErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && (state.data.length === 0
      ? <EmptyState title={t(empty[0] as MessageKey)} description={t(empty[1] as MessageKey)} />
      : <ul className="recipient-list" aria-label={t(filterLabel(filter as RecipientLifecycleStatus))}>
        {state.data.map((recipient) => <li key={recipient.id} className="recipient-card">
          <RecipientStatus status={recipient.lifecycleStatus} />
          <h2><Link to={`${listPath}/${recipient.id}`}><bdi>{recipient.name}</bdi></Link></h2>
          <p className="recipient-hint">{t('recipientRelationshipLabel')}</p>
          <p><bdi>{recipient.relationship || t('recipientRelationshipEmpty')}</bdi></p>
        </li>)}
      </ul>)}
  </RecipientLayout>;
}
export function CreateRecipientPage() {
  const { t } = useI18n(); const api = useRecipientsApi(); const navigate = useNavigate();
  return <RecipientLayout title={t('recipientCreate')} description={t('recipientCreateDescription')}>
    <div className="recipient-surface"><RecipientForm cancelTo={listPath} onSave={async (input, signal) => {
      const recipient = await api.create(input, signal);
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
  const date = (value: string) => new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium' }).format(new Date(value));
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
        {edit && state.data.lifecycleStatus === 'ACTIVE' ? <RecipientForm initial={state.data} cancelTo={`${listPath}/${id}`} onReload={reload} onSave={async (input, signal) => {
          await api.update(id, input, signal);
          if (!signal.aborted) navigate(`${listPath}/${id}`, { replace: true, state: { recipientNotice: 'updated' } });
        }} /> : <>
          <dl className="recipient-details">
            <div><dt>{t('recipientRelationshipLabel')}</dt><dd><bdi>{state.data.relationship || t('recipientRelationshipEmpty')}</bdi></dd></div>
            <div><dt>{t('recipientCreatedAt')}</dt><dd><time dateTime={state.data.createdAt}>{date(state.data.createdAt)}</time></dd></div>
            <div><dt>{t('recipientUpdatedAt')}</dt><dd><time dateTime={state.data.updatedAt}>{date(state.data.updatedAt)}</time></dd></div>
            {state.data.archivedAt && <div><dt>{t('recipientArchivedAt')}</dt><dd><time dateTime={state.data.archivedAt}>{date(state.data.archivedAt)}</time></dd></div>}
            {state.data.restoredAt && <div><dt>{t('recipientRestoredAt')}</dt><dd><time dateTime={state.data.restoredAt}>{date(state.data.restoredAt)}</time></dd></div>}
          </dl>
          {state.data.lifecycleStatus === 'ACTIVE' && <div className="recipient-actions">
            <ButtonLink to={`${listPath}/${id}/edit`}>{t('recipientEdit')}</ButtonLink>
            <Button variant="secondary" onClick={() => { setNotice(null); setConfirming('archive'); }}>{t('recipientArchive')}</Button>
          </div>}
          {state.data.lifecycleStatus === 'ARCHIVED' && <div className="recipient-actions">
            <Button variant="secondary" onClick={() => { setNotice(null); setConfirming('restore'); }}>{t('recipientRestore')}</Button>
          </div>}
          {edit && state.data.lifecycleStatus !== 'ACTIVE' && <ButtonLink variant="secondary" to={`${listPath}/${id}`}>{t('recipientDetails')}</ButtonLink>}
        </>}
      </div>
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
