import type { SummarySafety as Safety } from '../../api/summary';
import { useI18n } from '../../i18n/I18nProvider';

/** Kept outside model-content containers; no dismiss/collapse control. */
export function SummarySafety({ safety }: { safety: Safety }) {
  const { t } = useI18n();
  const urgent = safety.needsDoctorBanner || safety.incomplete || safety.signals.some((signal) => signal.severity !== 'INFO' && signal.severity !== 'WATCH');
  return <section className={`summary-safety${urgent ? ' summary-safety--urgent' : ''}`} aria-label={t('summarySafety')}>
    <h3>{t(safety.needsDoctorBanner ? 'summaryClinicalReview' : 'summarySafety')}</h3>
    {safety.incomplete && <p className="summary-safety-warning" role="alert">{t('summarySafetyUnknown')}</p>}
    {safety.signals.length > 0 && <ul className="summary-signals">{safety.signals.map((signal, index) => <li key={index}>
      <div><strong className="summary-severity"><bdi>{signal.severity ?? t('summaryUnknownSeverity')}</bdi></strong>{' '}
        <strong><bdi>{signal.ruleId ?? t('summaryUnknownSignal')}</bdi></strong></div>
      <p className="summary-untrusted" dir="auto">{signal.reason ?? t('summaryNoReason')}</p>
    </li>)}</ul>}
    {!safety.incomplete && !safety.signals.length && !safety.needsDoctorBanner && <p>{t('summaryNoFlags')}</p>}
  </section>;
}
