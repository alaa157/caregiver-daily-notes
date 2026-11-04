import { useCallback, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { isNoteDate, type DailyNote } from '../../api/notes';
import { recipientErrorMessage, type Recipient } from '../../api/recipients';
import { ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import { useNoteResource, useNotesApis } from '../notes/useNotes';
import './overview.css';

function localToday() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

type RecipientOverview = {
  recipient: Recipient;
  note: DailyNote | null;
  status: 'recorded' | 'missing' | 'error';
};

const workflows = [
  ['people', '/app/recipients', 'recipientsTitle', 'overviewPeopleHint'],
  ['edit', '/app/notes/new', 'notesCreate', 'overviewNotesHint'],
  ['history', '/app/notes', 'historyTitle', 'overviewHistoryHint'],
  ['journal', '/app/notes/summary', 'summaryTitle', 'overviewSummaryHint'],
] as const;

export function WorkspaceOverview() {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const [params, setParams] = useSearchParams();
  const today = useMemo(localToday, []);
  const rawDate = params.get('date');
  const date = rawDate && isNoteDate(rawDate) ? rawDate : today;
  const load = useCallback(async (signal: AbortSignal) => {
    const recipients = await api.recipients.list(signal);
    if (!recipients.length) return [];
    const notes: DailyNote[] = [];
    const rows = (unmatchedStatus: 'missing' | 'error') => {
      const notesByRecipient = new Map<string, DailyNote>();
      notes.filter((note) => note.date === date).forEach((note) => notesByRecipient.set(note.recipientId, note));
      return recipients.map((recipient): RecipientOverview => {
        const note = notesByRecipient.get(recipient.id) ?? null;
        return { recipient, note, status: note ? 'recorded' : unmatchedStatus };
      });
    };
    try {
      let page = await api.notes.history({ from: date, to: date, page: 0, size: 100 }, signal);
      notes.push(...page.content);
      for (let number = 1; number < page.totalPages; number += 1) {
        page = await api.notes.history({ from: date, to: date, page: number, size: 100 }, signal);
        notes.push(...page.content);
      }
      return rows('missing');
    } catch (error) {
      if (signal.aborted) throw error;
      return rows('error');
    }
  }, [api, date]);
  const { state, reload } = useNoteResource(load);
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
    {state.status === 'loading' && <LoadingState label={t('overviewLoading')} />}
    {state.status === 'error' && <ErrorState description={t(recipientErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && (state.data.length === 0
      ? <div className="overview-empty"><EmptyState title={t('recipientsEmpty')} description={t('overviewNoRecipients')} />
        <ButtonLink to="/app/recipients/new">{t('recipientCreate')}</ButtonLink></div>
      : <>
        <h2 className="overview-section-title">{t('overviewRecipientStatus')}</h2>
        <ul className="overview-list">
          {state.data.map(({ recipient, note, status }) => <li key={recipient.id} className="overview-row">
            <div className="overview-person"><span className="person-avatar"><Icon name="people" size={20} /></span><Link to={`/app/recipients/${recipient.id}`}><bdi>{recipient.name}</bdi></Link>
              <span className="overview-secondary"><bdi>{recipient.relationship || t('recipientRelationshipEmpty')}</bdi></span>
            </div>
            <div className="overview-note-status" aria-live="polite">
              {status === 'recorded' && <><span className="overview-status overview-status--recorded">{t('overviewNoteRecorded')}</span>
                <Link to={`/app/notes/${note!.id}`}>{t('overviewOpenNote')}</Link></>}
              {status === 'missing' && <><span className="overview-status">{t('overviewNoNote')}</span>
                {recipient.active && <Link to={`/app/notes/new?recipientId=${encodeURIComponent(recipient.id)}&date=${encodeURIComponent(date)}`}>{t('overviewRecordNote')}</Link>}</>}
              {status === 'error' && <><span className="overview-status overview-status--error">{t('overviewNoteStatusUnavailable')}</span>
                <button type="button" className="overview-retry" onClick={reload}>{t('retry')}</button></>}
            </div>
            {!recipient.active && <span className="overview-inactive">{t('recipientInactive')}</span>}
          </li>)}
        </ul>
        <p className="overview-footnote">{t('overviewMissingIsNotNegative')}</p>
        {state.data.some((row) => row.status === 'error') && <p className="overview-partial" role="status">{t('overviewPartialData')}</p>}
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
