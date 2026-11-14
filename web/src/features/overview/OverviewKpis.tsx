import type { OverviewState } from './useDashboardOverview';
import { getDashboardKpis } from './dashboardKpis';
import { useI18n } from '../../i18n/I18nProvider';
import type { MonthToDateMetric } from './useMonthToDateNotes';

export function OverviewKpis({ state, monthToDate, onRetryMonthToDate }: {
  state: OverviewState;
  monthToDate?: MonthToDateMetric;
  onRetryMonthToDate?: () => void;
}) {
  const { t, language } = useI18n();
  // Counts are formatted with the active locale so Arabic renders Arabic-Indic digits.
  const number = (value: number) => new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  const kpis = getDashboardKpis(state, {
    recipients: t('overviewKpiRecipients'),
    recorded: t('overviewKpiRecorded'),
    notRecorded: t('overviewKpiNotRecorded'),
  });

  return <section className="overview-kpis" aria-labelledby="overview-kpis-title">
    <h2 id="overview-kpis-title" className="overview-section-title">{t('overviewKpisTitle')}</h2>
    <ul className="overview-kpi-list">
      {kpis.map((kpi) => <li className="overview-kpi-card" key={kpi.label}>
        <h3>{kpi.label}</h3>
        <output className={`overview-kpi-value overview-kpi-value--${kpi.state}`} aria-label={kpi.label}>
          {kpi.state === 'ready' && kpi.value !== null ? <bdi>{number(kpi.value)}</bdi> : t(kpi.state === 'loading' ? 'loading' : 'overviewKpiUnavailable')}
        </output>
      </li>)}
      {monthToDate && <li className="overview-kpi-card overview-mtd">
        <div className="overview-mtd-region" role="region" aria-label={t('overviewMtdLabel')} aria-busy={monthToDate.state === 'loading'}>
          <h3 id="overview-mtd-label">{t('overviewMtdLabel')}</h3>
          <strong role={monthToDate.state === 'error' ? 'alert' : 'status'}
            aria-label={t('overviewMtdLabel')} aria-live={monthToDate.state === 'error' ? undefined : 'polite'}>
            {monthToDate.state === 'ready' ? number(monthToDate.total)
              : monthToDate.state === 'loading' ? t('overviewMtdLoading') : t('overviewMtdError')}
          </strong>
          {monthToDate.state === 'error' && onRetryMonthToDate && <button type="button" className="overview-retry"
            aria-labelledby="overview-mtd-label overview-mtd-retry-label" onClick={onRetryMonthToDate}>
            <span id="overview-mtd-retry-label">{t('retry')}</span>
          </button>}
        </div>
      </li>}
    </ul>
  </section>;
}
