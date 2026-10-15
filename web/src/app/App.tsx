import { Route, Routes } from 'react-router-dom';
import { AppShell } from '../components/AppShell';
import { ButtonLink } from '../components/Button';
import { CareIllustration } from '../components/CareIllustration';
import { FeatureCard } from '../components/FeatureCard';
import { Icon } from '../components/Icon';
import { PagePanel } from '../components/PagePanel';
import { useI18n } from '../i18n/I18nProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { ProtectedRoute } from './ProtectedRoute';

function HomePage() {
  const { t } = useI18n();
  return (
    <>
      <section className="hero container" aria-labelledby="hero-heading">
        <div className="hero-copy">
          <p className="eyebrow hero-eyebrow"><span className="eyebrow-dot" aria-hidden="true" />{t('eyebrow')}</p>
          <h1 id="hero-heading">{t('heroTitle')}</h1>
          <p className="hero-description">{t('heroDescription')}</p>
          <div className="hero-actions">
            <ButtonLink to="/login">{t('begin')}<Icon name="arrow-right" className="directional-icon" /></ButtonLink>
            <a className="button button--text" href="#foundation">{t('explore')}<span aria-hidden="true">↓</span></a>
          </div>
          <p className="disclaimer"><Icon name="shield" size={16} />{t('note')}</p>
        </div>
        <CareIllustration />
      </section>
      <section id="foundation" className="foundation-section" aria-labelledby="foundation-heading">
        <div className="container">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{t('foundationLabel')}</p>
              <h2 id="foundation-heading">{t('foundationTitle')}</h2>
            </div>
            <p className="section-description">{t('foundationDescription')}</p>
          </div>
          <div className="features-grid">
            <FeatureCard icon="journal" title={t('featureOne')} description={t('featureOneDescription')} status={t('comingSoon')} />
            <FeatureCard icon="globe" title={t('featureTwo')} description={t('featureTwoDescription')} status={t('availableNow')} available />
            <FeatureCard icon="shield" title={t('featureThree')} description={t('featureThreeDescription')} status={t('ourApproach')} />
          </div>
          <div className="foundation-notice"><Icon name="info" size={18} /><p>{t('foundationNotice')}</p></div>
        </div>
      </section>
    </>
  );
}

function DashboardPage() {
  const { t } = useI18n();
  return <PagePanel icon="journal" eyebrow={t('dashboard')} title={t('dashboardTitle')} description={t('dashboardDescription')} />;
}

function NotFoundPage() {
  const { t } = useI18n();
  return (
    <PagePanel icon="leaf" eyebrow={t('notFoundLabel')} title={t('pageNotFound')} description={t('pageNotFoundDescription')}>
      <ButtonLink variant="secondary" to="/"><Icon name="arrow-left" className="directional-icon" />{t('returnHome')}</ButtonLink>
    </PagePanel>
  );
}

export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/app" element={<DashboardPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}
