import { Link } from 'react-router-dom';
import { isRecipientId } from '../../api/recipients';
import type { SummaryRequest, SummaryResponse } from '../../api/summary';
import { useI18n } from '../../i18n/I18nProvider';
import { SummarySafety } from '../safety/SummarySafety';

export interface SummaryView { request: SummaryRequest; recipientName: string; response: SummaryResponse; failed?: boolean }
const sampleSizeKey = (metric: string) => {
  if (metric === 'MISSED_MED_COUNT' || metric === 'POOR_APPETITE_COUNT') return 'summarySampleSizeDays';
  if (metric === 'FALL_COUNT' || metric === 'HIGH_PAIN_COUNT') return 'summarySampleSizeObservations';
  return 'summarySampleSize';
};
export function SummaryResult({ view }: { view: SummaryView }) {
  const { t, language } = useI18n();
  const { response, request } = view;
  const from = new Date(`${request.periodEndInclusive}T00:00:00Z`);
  from.setUTCDate(from.getUTCDate() - request.periodDays + 1);
  const format = (value: number) => new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  const canPresentAiSuccess = response.summary !== null && !response.summary.aiUnavailable
    && !response.invalidContent && !view.failed && response.summary.observations.length > 0;
  const canPresentUncertainty = response.summary !== null && !response.invalidContent && !view.failed;
  const headingKey = view.failed ? 'summaryFailureSafety'
    : response.invalidContent ? 'summaryInvalidSummaryTitle'
      : response.summary?.aiUnavailable ? 'summaryUnavailable' : 'summaryResult';
  return <section className="summary-result" aria-labelledby="summary-result-heading">
    <header className="summary-result-heading"><h2 id="summary-result-heading">{t(headingKey)}</h2>
      <p className="summary-recipient"><bdi>{view.recipientName}</bdi></p>
      <p>{t('summaryCoverage')}: <bdi>{from.toISOString().slice(0, 10)}</bdi> – <bdi>{request.periodEndInclusive}</bdi> (<bdi>{format(request.periodDays)}</bdi> {t('summaryDays')})</p>
    </header>
    <SummarySafety safety={response.safety} recipientId={request.recipientId}
      periodEndInclusive={request.periodEndInclusive} periodDays={request.periodDays} />
    {response.invalidContent && <p className="summary-notice" role="alert">{t('summaryInvalidContent')}</p>}
    {response.summary?.aiUnavailable && !view.failed && <p className="summary-notice" role="status">{t('summaryFallback')}</p>}
    {canPresentAiSuccess && <section className="summary-surface summary-ai" aria-labelledby="summary-observations-heading">
      <div className="summary-ai-heading">
        <h3 id="summary-observations-heading">{t('summaryObservations')}</h3>
        <span className="summary-ai-label">{t('summaryAiGeneratedLabel')}</span>
      </div>
      <p className="summary-educational-disclaimer">{t('summaryEducationalDisclaimer')}</p>
      <ol className="summary-observations">{response.summary!.observations.map((observation, index) => <li key={index}>
        <p className="summary-untrusted" dir="auto">{observation.text}</p>
        <div className="summary-evidence"><h4>{t('summaryEvidence')}</h4>
          <blockquote className="summary-untrusted" dir="auto">{observation.quote}</blockquote>
          <p>{isRecipientId(observation.noteId)
            ? <Link to={`/app/notes/${encodeURIComponent(observation.noteId)}`}>{t('summarySourceNote')}: <bdi>{observation.noteId}</bdi></Link>
            : <>{t('summaryUnlinkedSource')}: <bdi>{observation.noteId}</bdi></>}</p>
        </div>
      </li>)}</ol>
    </section>}
    {response.summary && !response.summary.aiUnavailable && !response.invalidContent && !view.failed
      && response.summary.observations.length === 0 && <p className="summary-notice">{t('summaryNoObservations')}</p>}
    {canPresentUncertainty && <section className="summary-surface summary-uncertainties" aria-labelledby="summary-uncertainty-heading">
      <h3 id="summary-uncertainty-heading">{t('summaryUncertainty')}</h3>
      {response.summary!.uncertainties.length ? <ul>{response.summary!.uncertainties.map((uncertainty, index) => <li key={index}>
        <strong className="summary-untrusted" dir="auto">{uncertainty.topic}</strong><p className="summary-untrusted" dir="auto">{uncertainty.detail}</p>
      </li>)}</ul> : <p>{t('summaryNoUncertainty')}</p>}
    </section>}
    {!view.failed && response.trends.length > 0 && <section className="summary-surface" aria-labelledby="summary-trends-heading">
      <h3 id="summary-trends-heading">{t('summaryTrends')}</h3>
      <ul className="summary-trends">{response.trends.map((trend, index) => <li key={index}>
        <h4 className="summary-untrusted" dir="auto">{trend.metric}</h4>
        <dl>
          <div><dt>{t('summaryPreviousPeriod')}</dt><dd>{trend.previousSampleSize === 0 ? <span role="status">{t('summaryNoRecordedData')}</span> : <>
            <span>{t('summaryEventCount')}: <bdi>{format(trend.previousCount)}</bdi></span><br />
            <span>{t(sampleSizeKey(trend.metric))}: <bdi>{format(trend.previousSampleSize)}</bdi></span></>}</dd></div>
          <div><dt>{t('summaryRequestedPeriod')}</dt><dd>{trend.currentSampleSize === 0 ? <span role="status">{t('summaryNoRecordedData')}</span> : <>
            <span>{t('summaryEventCount')}: <bdi>{format(trend.currentCount)}</bdi></span><br />
            <span>{t(sampleSizeKey(trend.metric))}: <bdi>{format(trend.currentSampleSize)}</bdi></span></>}</dd></div>
          {trend.previousSampleSize > 0 && trend.currentSampleSize > 0 && <div><dt>{t('summaryDelta')}</dt><dd><bdi>{format(trend.delta)}</bdi></dd></div>}
        </dl>
      </li>)}</ul>
    </section>}
  </section>;
}
