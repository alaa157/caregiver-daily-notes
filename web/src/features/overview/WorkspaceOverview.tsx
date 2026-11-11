import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { isNoteDate } from '../../api/notes';
import { recipientErrorMessage } from '../../api/recipients';
import { ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import { OverviewKpis } from './OverviewKpis';
import { useDashboardOverview } from './useDashboardOverview';
import { RecipientCard } from './RecipientCard';
import { useMonthToDateNotes } from './useMonthToDateNotes';
import './overview.css';

function localToday() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

const workflows = [
  ['people', '/app/recipients', 'recipientsTitle', 'overviewPeopleHint'],
  ['edit', '/app/notes/new', 'notesCreate', 'overviewNotesHint'],
  ['history', '/app/notes', 'historyTitle', 'overviewHistoryHint'],
  ['journal', '/app/notes/summary', 'summaryTitle', 'overviewSummaryHint'],
] as const;

export function WorkspaceOverview() {
  const { t, language } = useI18n();
  const [params, setParams] = useSearchParams();
  const today = useMemo(localToday, []);
  const rawDate = params.get('date');
  const date = rawDate && isNoteDate(rawDate) ? rawDate : today;
  const { state, reload } = useDashboardOverview(date);
  const { metric: monthToDate, reload: reloadMonthToDate } = useMonthToDateNotes(date);
  const overviewData = state.status === 'ready' || state.status === 'partial' ? state : null;
  const updateDate = (value: string) => {
    const next = new URLSearchParams(params);
    if (isNoteDate(value)) next.set('date', value);
    else next.delete('date');
    setParams(next);
  };
  const formattedDate = (value: string) => new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`));

  return <section className="workspace-overview container" aria-labelledby="workspace-overview-title">
    <header className="overview-heading">
      <div><p className="eyebrow">{t('dashboard')}</p><h1 id="workspace-overview-title">{t('dashboardTitle')}</h1><p>{t('overviewDescription')}</p></div>
      <div className="overview-actions"><ButtonLink variant="secondary" to="/app/recipients"><Icon name="people" size={18} />{t('recipientsTitle')}</ButtonLink><ButtonLink to="/app/notes/new"><Icon name="plus" size={18} />{t('notesCreate')}</ButtonLink></div>
    </header>
    <div className="overview-date"><Icon name="calendar" size={20} /><label htmlFor="overview-date">{t('overviewDateLabel')}</label>
      <input id="overview-date" type="date" dir="ltr" value={date} onChange={(event) => updateDate(event.target.value)} />
      <time dateTime={date}>{formattedDate(date)}</time>
    </div>
    <OverviewKpis state={state} />
    <section className="overview-mtd" aria-label={t('overviewMtdLabel')}>
      <span>{t('overviewMtdLabel')}</span>
      <strong aria-live="polite">{monthToDate.state === 'ready'
        ? new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(monthToDate.total!)
        : monthToDate.state === 'loading' ? t('overviewMtdLoading') : t('overviewMtdError')}</strong>
      {monthToDate.state === 'error' && <button type="button" className="overview-retry" onClick={reloadMonthToDate}>{t('retry')}</button>}
    </section>
    {state.status === 'loading' && <LoadingState label={t('overviewLoading')} />}
    {state.status === 'error' && <ErrorState description={t(recipientErrorMessage(state.error))} onRetry={reload} />}
    {overviewData && (overviewData.data.length === 0
      ? <div className="overview-empty"><EmptyState title={t('recipientsEmpty')} description={t('overviewNoRecipients')} />
        <ButtonLink to="/app/recipients/new">{t('recipientCreate')}</ButtonLink></div>
      : <>
        <h2 className="overview-section-title">{t('overviewRecipientStatus')}</h2>
        <ul className="overview-list">
          {overviewData.data.map((row) => <RecipientCard key={row.recipient.id} row={row} date={date} onRetry={reload} />)}
        </ul>
        <p className="overview-footnote">{t('overviewMissingIsNotNegative')}</p>
        {overviewData.status === 'partial' && <p className="overview-partial" role="status">{t('overviewPartialData')}</p>}
      </>)}
    <section className="overview-routine" aria-labelledby="routine-title">
      <h2 id="routine-title">{t('overviewQuickActions')}</h2>
      <nav className="routine-grid" aria-label={t('overviewMoreWorkflows')}>
        {workflows.map(([icon, to, title, hint]) => <div className="routine-link" key={to}>
          <span className="routine-icon"><Icon name={icon} /></span>
          <Link to={to} aria-describedby={`routine-${icon}-hint`}>{t(title)}</Link>
          <span id={`routine-${icon}-hint`}>{t(hint)}</span>
          <Icon name="arrow-right" size={17} className="directional-icon" />
        </div>)}
      </nav>
    </section>
  </section>;
}
