import type { OverviewState } from './useDashboardOverview';
import { getDashboardKpis } from './dashboardKpis';
import { useI18n } from '../../i18n/I18nProvider';

export function OverviewKpis({ state }: { state: OverviewState }) {
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
    </ul>
  </section>;
}
