import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { isNoteDate } from '../../api/notes';
import { createRecipientsApi } from '../../api/recipients';
import { createSummaryApi, readSummarySafety, retainSafety, summaryErrorMessage, summaryPeriods, type SummaryRequest } from '../../api/summary';
import { Button, ButtonLink } from '../../components/Button';
import { ErrorState, FieldError, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { ApiError, useApiClient } from '../../lib/api';
import { useByok } from '../ai/ByokContext';
import { useNoteResource } from '../notes/useNotes';
import { SummaryResult, type SummaryView } from './SummaryResult';
import './summary.css';

export const SUMMARY_TIMEOUT_MS = 120_000;
function today() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`; }
const sameRequest = (a: SummaryRequest, b: SummaryRequest) => a.recipientId === b.recipientId && a.periodDays === b.periodDays && a.periodEndInclusive === b.periodEndInclusive;

export function SummaryPage() {
  const { t, language } = useI18n();
  const client = useApiClient();
  const { credentials } = useByok();
  const api = useMemo(() => createSummaryApi(client), [client]);
  const recipientsApi = useMemo(() => createRecipientsApi(client), [client]);
  const loadRecipients = useCallback((signal: AbortSignal) => recipientsApi.list(signal, 'ALL'), [recipientsApi]);
  const recipients = useNoteResource(loadRecipients);
  const [search, setSearch] = useSearchParams();
  const recipientId = search.get('recipientId') ?? '';
  const [periodDays, setPeriodDays] = useState<SummaryRequest['periodDays']>(7);
  const [periodEndInclusive, setEnd] = useState(today);
  const [errors, setErrors] = useState<Partial<Record<'recipient' | 'date', MessageKey>>>({});
  const [view, setView] = useState<SummaryView>();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<MessageKey>();
  const [cancelled, setCancelled] = useState(false);
  const active = useRef<AbortController | null>(null);
  const timeout = useRef<ReturnType<typeof setTimeout>>();
  const recipientField = useRef<HTMLSelectElement>(null);
  const dateField = useRef<HTMLInputElement>(null);
  const resultNotice = useRef<HTMLDivElement>(null);
  useEffect(() => () => { active.current?.abort(); active.current = null; clearTimeout(timeout.current); }, [api]);
  useEffect(() => {
    if (!pending && (view || error || cancelled)) resultNotice.current?.focus();
  }, [pending, view, error, cancelled]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (active.current) return;
    const recipient = recipients.state.status === 'ready' ? recipients.state.data.find((item) => item.id === recipientId) : undefined;
    const invalid: typeof errors = {};
    if (!recipient) invalid.recipient = 'summaryRequiredRecipient';
    if (!isNoteDate(periodEndInclusive)) invalid.date = 'summaryRequiredDate';
    setErrors(invalid);
    if (Object.keys(invalid).length) { (invalid.recipient ? recipientField.current : dateField.current)?.focus(); return; }
    if (!credentials) {
      setError('summaryByokMissingCredentials');
      setCancelled(false);
      return;
    }
    const request = { recipientId, periodDays, periodEndInclusive };
    const controller = new AbortController();
    active.current = controller;
    setPending(true); setError(undefined); setCancelled(false);
    const finish = () => { active.current = null; setPending(false); };
    const timer = setTimeout(() => {
      if (active.current !== controller) return;
      controller.abort(); setError('summaryTimeout'); finish();
    }, SUMMARY_TIMEOUT_MS);
    timeout.current = timer;
    try {
      const response = await api.generateByok({ summary: request, credentials }, controller.signal);
      if (active.current !== controller || controller.signal.aborted) return;
      setView((previous) => ({ request, recipientName: recipient!.name, response: {
        ...response, safety: retainSafety(previous && sameRequest(previous.request, request) ? previous.response.safety : undefined, response.safety),
      } }));
    } catch (failure: unknown) {
      if (active.current !== controller || controller.signal.aborted) return;
      // Some APIs return authoritative safety alongside a generation error.
      if (failure instanceof ApiError && failure.details && typeof failure.details === 'object' && 'safety' in failure.details) {
        const safety = readSummarySafety(failure.details.safety);
        setView((previous) => {
          const matching = previous && sameRequest(previous.request, request) ? previous : undefined;
          return { request, recipientName: recipient!.name, failed: true, response: {
            summary: matching?.response.summary ?? null, trends: matching?.response.trends ?? [], invalidContent: false,
            safety: retainSafety(matching?.response.safety, safety, true),
          } };
        });
      }
      setError(summaryErrorMessage(failure));
    } finally {
      clearTimeout(timer);
      if (active.current === controller) finish();
    }
  }
  function cancel() {
    active.current?.abort(); active.current = null; clearTimeout(timeout.current); setPending(false); setCancelled(true);
  }
  const available = recipients.state.status === 'ready' && recipients.state.data.length > 0;
  const format = (value: number) => new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  return <section className="summary-page container" aria-labelledby="summary-heading">
    <header className="summary-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="summary-heading">{t('summaryTitle')}</h1><p>{t('summaryDescription')}</p></div>
      <ButtonLink variant="secondary" to="/app/notes">{t('historyTitle')}</ButtonLink>
    </header>
    <aside id="summary-disclaimer" className="summary-disclaimer" aria-label={t('note')}>{t('summaryDisclaimer')}</aside>
    {recipients.state.status === 'loading' && <LoadingState label={t('summaryRecipientsLoading')} />}
    {recipients.state.status === 'error' && <ErrorState description={t('summaryRecipientsError')} onRetry={recipients.reload} />}
    {recipients.state.status === 'ready' && !available && <div className="summary-notice"><p>{t('summaryNoRecipients')}</p><ButtonLink to="/app/recipients/new">{t('recipientCreate')}</ButtonLink></div>}
    <form className="summary-surface summary-controls" onSubmit={(event) => void submit(event)} noValidate aria-describedby="summary-disclaimer">
      <div className="summary-fields">
        <div className="summary-field"><label htmlFor="summary-recipient">{t('summaryRecipient')}</label>
          <select ref={recipientField} id="summary-recipient" value={recipientId} disabled={pending || !available} aria-invalid={!!errors.recipient} aria-describedby={errors.recipient ? 'summary-recipient-error' : undefined}
            onChange={(event) => {
              const next = new URLSearchParams(search);
              if (event.target.value) next.set('recipientId', event.target.value);
              else next.delete('recipientId');
              setSearch(next); setErrors({});
            }}>
            <option value="">{t('summaryChooseRecipient')}</option>
            {recipients.state.status === 'ready' && recipients.state.data.map((recipient) => <option key={recipient.id} value={recipient.id} dir="auto">{recipient.name}{recipient.lifecycleStatus === 'ARCHIVED' ? ` — ${t('recipientArchived')}` : recipient.lifecycleStatus === 'LEGACY_INACTIVE_REVIEW' ? ` — ${t('recipientNeedsReview')}` : ''}</option>)}
          </select>
          {errors.recipient && <FieldError id="summary-recipient-error" message={t(errors.recipient)} />}
        </div>
        <div className="summary-field"><label htmlFor="summary-end">{t('summaryEndDate')}</label>
          <input ref={dateField} id="summary-end" type="date" value={periodEndInclusive} disabled={pending} aria-invalid={!!errors.date} aria-describedby={errors.date ? 'summary-date-error' : undefined} onChange={(event) => { setEnd(event.target.value); setErrors({}); }} />
          {errors.date && <FieldError id="summary-date-error" message={t(errors.date)} />}
        </div>
      </div>
      <fieldset className="summary-periods" disabled={pending}><legend>{t('summaryPeriod')}</legend>
        {summaryPeriods.map((period) => <label key={period}><input type="radio" name="summary-period" value={period} checked={periodDays === period} onChange={() => setPeriodDays(period)} />{format(period)} {t('summaryDays')}</label>)}
      </fieldset>
      <div className="summary-actions"><Button type="submit" disabled={pending || !available}>{t(view ? 'summaryRegenerate' : 'summaryGenerate')}</Button>
        {pending && <Button variant="secondary" onClick={cancel}>{t('summaryCancel')}</Button>}</div>
    </form>
    <div ref={resultNotice} tabIndex={-1} className="summary-progress">
      {pending && <LoadingState label={t('summaryGenerating')} />}
      {pending && view && <p>{t('summaryWhileLoading')}</p>}
      {cancelled && <p role="status">{t('summaryCancelled')}</p>}
      {error && <ErrorState description={t(error)} />}
      {!view && !pending && !error && <p className="summary-idle">{t('summaryIdle')}</p>}
      {view && <><p className="summary-context-notice">{t('summaryPrevious')}</p><SummaryResult view={view} /></>}
    </div>
  </section>;
}
