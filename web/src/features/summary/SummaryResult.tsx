import { Link } from 'react-router-dom';
import { isRecipientId } from '../../api/recipients';
import type { SummaryRequest, SummaryResponse } from '../../api/summary';
import { useI18n } from '../../i18n/I18nProvider';
import { SummarySafety } from '../safety/SummarySafety';

export interface SummaryView { request: SummaryRequest; recipientName: string; response: SummaryResponse; failed?: boolean }
export function SummaryResult({ view }: { view: SummaryView }) {
  const { t, language } = useI18n();
  const { response, request } = view;
  const from = new Date(`${request.periodEndInclusive}T00:00:00Z`);
  from.setUTCDate(from.getUTCDate() - request.periodDays + 1);
  const format = (value: number) => new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  const canPresentAiSuccess = response.summary !== null && !response.summary.aiUnavailable
    && !response.invalidContent && !view.failed && response.summary.observations.length > 0;
  const headingKey = view.failed ? 'summaryFailureSafety' : response.invalidContent ? 'summaryInvalidSummaryTitle' : 'summaryResult';
  return <section className="summary-result" aria-labelledby="summary-result-heading">
    <header className="summary-result-heading"><h2 id="summary-result-heading">{t(headingKey)}</h2>
      <p className="summary-recipient"><bdi>{view.recipientName}</bdi></p>
      <p>{t('summaryCoverage')}: <bdi>{from.toISOString().slice(0, 10)}</bdi> – <bdi>{request.periodEndInclusive}</bdi> ({format(request.periodDays)} {t('summaryDays')})</p>
    </header>
    <SummarySafety safety={response.safety} />
    {response.invalidContent && <p className="summary-notice" role="alert">{t('summaryInvalidContent')}</p>}
    {response.summary?.aiUnavailable && <p className="summary-notice" role="status">{t('summaryFallback')}</p>}
    {response.summary && <>
      {!response.summary.aiUnavailable && <section className="summary-surface summary-ai" aria-labelledby="summary-observations-heading">
        <div className="summary-ai-heading">
          <h3 id="summary-observations-heading">{t('summaryObservations')}</h3>
          {canPresentAiSuccess && <span className="summary-ai-label">{t('summaryAiGeneratedLabel')}</span>}
        </div>
        {canPresentAiSuccess && <p className="summary-educational-disclaimer">{t('summaryEducationalDisclaimer')}</p>}
        {!response.summary.observations.length && <p>{t('summaryNoObservations')}</p>}
        {!!response.summary.observations.length && <ol className="summary-observations">{response.summary.observations.map((observation, index) => <li key={index}>
          <p className="summary-untrusted" dir="auto">{observation.text}</p>
          <div className="summary-evidence"><h4>{t('summaryEvidence')}</h4>
            <blockquote className="summary-untrusted" dir="auto">{observation.quote}</blockquote>
            <p>{isRecipientId(observation.noteId)
              ? <Link to={`/app/notes/${encodeURIComponent(observation.noteId)}`}>{t('summarySourceNote')}: <bdi>{observation.noteId}</bdi></Link>
              : <>{t('summaryUnlinkedSource')}: <bdi>{observation.noteId}</bdi></>}</p>
          </div>
        </li>)}</ol>}
      </section>}
      <section className="summary-surface summary-uncertainties" aria-labelledby="summary-uncertainty-heading">
        <h3 id="summary-uncertainty-heading">{t('summaryUncertainty')}</h3>
        {response.summary.uncertainties.length ? <ul>{response.summary.uncertainties.map((uncertainty, index) => <li key={index}>
          <strong className="summary-untrusted" dir="auto">{uncertainty.topic}</strong><p className="summary-untrusted" dir="auto">{uncertainty.detail}</p>
        </li>)}</ul> : <p>{t('summaryNoUncertainty')}</p>}
      </section>
    </>}
    {response.trends.length > 0 && <section className="summary-surface" aria-labelledby="summary-trends-heading">
      <h3 id="summary-trends-heading">{t('summaryTrends')}</h3>
      <ul className="summary-trends">{response.trends.map((trend, index) => <li key={index}>
        <h4 className="summary-untrusted" dir="auto">{trend.metric}</h4>
        <dl><div><dt>{t('summaryPreviousCount')}</dt><dd>{format(trend.previousCount)}</dd></div>
          <div><dt>{t('summaryCurrentCount')}</dt><dd>{format(trend.currentCount)}</dd></div>
          <div><dt>{t('summaryDelta')}</dt><dd><bdi>{format(trend.delta)}</bdi></dd></div></dl>
      </li>)}</ul>
    </section>}
  </section>;
}
