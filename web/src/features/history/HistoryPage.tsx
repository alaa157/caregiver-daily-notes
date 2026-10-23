import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { noteErrorMessage } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { useNoteResource, useNotesApis } from '../notes/useNotes';
import { HistoryFilterForm } from './HistoryFilterForm';
import { HistoryNoteCard } from './HistoryNoteCard';
import { defaultHistoryFilters, historyPageSizes, historySearch, readHistorySearch, validateHistoryFilters, type HistoryFilters } from './historyFilters';
import '../notes/notes.css';
import './history.css';

function HistoryResults({ filters: { recipientId, from, to }, page, size, recipients, onPage, focusResults }: {
  filters: HistoryFilters; page: number; size: number; recipients: Recipient[];
  onPage: (page: number) => void; focusResults: boolean;
}) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const heading = useRef<HTMLHeadingElement>(null);
  const load = useCallback((signal: AbortSignal) => api.notes.history({ from, to, recipientId: recipientId || undefined, page, size }, signal), [api, recipientId, from, to, page, size]);
  const { state, reload } = useNoteResource(load);
  useEffect(() => { if (state.status === 'ready' && focusResults) heading.current?.focus(); }, [state.status, focusResults]);
  const number = (value: number) => new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  return <section className="history-results" aria-labelledby="history-results-title" aria-busy={state.status === 'loading'}>
    <h2 ref={heading} id="history-results-title" tabIndex={-1}>{t('historyResults')}</h2>
    {state.status === 'loading' && <LoadingState label={t('historyLoading')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && <>
      <p className="history-result-count" role="status">{t('historyShowing')} {number(state.data.content.length ? page * size + 1 : 0)}–{number(state.data.content.length ? page * size + state.data.content.length : 0)} {t('historyOf')} {number(state.data.totalElements)} {t('historyNotes')}</p>
      <p className="note-hint history-order">{t('historyNewest')}</p>
      {state.data.content.length === 0 ? <div className="note-surface">
        <EmptyState title={t(state.data.totalElements ? 'historyEmptyPage' : 'historyNoNotes')} description={t(state.data.totalElements ? 'historyEmptyPageDescription' : 'historyNoNotesDescription')} />
        {page > 0 && <Button variant="secondary" onClick={() => onPage(0)}>{t('historyFirstPage')}</Button>}
      </div> : <ol className="history-list" role="list">{state.data.content.map((note) => <li key={note.id}>
        <HistoryNoteCard note={note} recipient={recipients.find((recipient) => recipient.id === note.recipientId)} />
      </li>)}</ol>}
      {(state.data.totalPages > 1 || page > 0) && <nav className="history-pagination" aria-label={t('historyPagination')}>
        <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>{t('historyPrevious')}</Button>
        <span>{t('historyPage')} {number(page + 1)} {t('historyOf')} {number(state.data.totalPages)}</span>
        <Button variant="secondary" disabled={state.data.last} onClick={() => onPage(page + 1)}>{t('historyNext')}</Button>
      </nav>}
    </>}
  </section>;
}

export function HistoryPage() {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const [defaults] = useState(defaultHistoryFilters);
  const [search, setSearch] = useSearchParams();
  const { filters, page, size } = readHistorySearch(search, defaults);
  const [focusResults, setFocusResults] = useState(false);
  const loadRecipients = useCallback((signal: AbortSignal) => api.recipients.list(signal), [api]);
  const recipients = useNoteResource(loadRecipients);
  const update = (next: HistoryFilters, nextPage = 0, nextSize = size) => {
    setFocusResults(true);
    setSearch(historySearch(next, nextPage, nextSize));
  };
  return <section className="notes-page container" aria-labelledby="history-heading">
    <header className="notes-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="history-heading">{t('historyTitle')}</h1><p>{t('historyDescription')}</p></div>
      <ButtonLink to="/app/notes/new">{t('notesCreate')}</ButtonLink>
    </header>
    {recipients.state.status === 'error' && <ErrorState description={t('historyRecipientsError')} onRetry={recipients.reload} />}
    <HistoryFilterForm filters={filters} defaults={defaults} recipients={recipients.state.status === 'ready' ? recipients.state.data : []} recipientsLoading={recipients.state.status === 'loading'} onApply={(next) => update(next)} onReset={() => update(defaults, 0, 20)} />
    <div className="note-field history-page-size"><label htmlFor="history-page-size">{t('historyPageSize')}</label>
      <select id="history-page-size" value={size} onChange={(event) => update(filters, 0, Number(event.target.value))}>
        {historyPageSizes.map((value) => <option key={value} value={value}>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value)}</option>)}
      </select>
    </div>
    {Object.keys(validateHistoryFilters(filters)).length ? <ErrorState description={t('historyFixFilters')} />
      : <HistoryResults key={`${filters.recipientId}:${filters.from}:${filters.to}:${page}:${size}`} filters={filters} page={page} size={size}
        recipients={recipients.state.status === 'ready' ? recipients.state.data : []} focusResults={focusResults} onPage={(next) => update(filters, next)} />}
  </section>;
}
