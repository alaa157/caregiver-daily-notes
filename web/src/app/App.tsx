import { Link, Route, Routes } from 'react-router-dom';
import { AppShell } from '../components/AppShell';
import { useI18n } from '../i18n/I18nProvider';
import { ProtectedRoute } from './ProtectedRoute';

function HomePage() {
  const { t } = useI18n();
  return <>
    <section className="hero container" aria-labelledby="hero-heading">
      <div className="hero-copy">
        <p className="eyebrow"><span className="eyebrow-dot" />{t('eyebrow')}</p>
        <h1 id="hero-heading">{t('heroTitle')}</h1>
        <p className="hero-description">{t('heroDescription')}</p>
        <Link to="/login" className="button button--primary">{t('begin')} <span aria-hidden="true">↗</span></Link>
        <p className="disclaimer">{t('note')}</p>
      </div>
      <div className="hero-visual" aria-hidden="true">
        <div className="visual-orbit orbit-one" /><div className="visual-orbit orbit-two" />
        <div className="visual-center">✳</div>
        <div className="visual-line visual-line-one" /><div className="visual-line visual-line-two" />
        <div className="visual-pill visual-pill-one" /><div className="visual-pill visual-pill-two" />
      </div>
    </section>
    <section className="section container" aria-labelledby="foundation-heading">
      <p className="eyebrow section-label">{t('foundationLabel')}</p>
      <h2 id="foundation-heading">{t('foundationTitle')}</h2>
      <p className="section-description">{t('foundationDescription')}</p>
      <div className="features-grid">
        <article className="feature-card"><span className="feature-number">01</span><h3>{t('featureOne')}</h3><p>{t('featureOneDescription')}</p></article>
        <article className="feature-card"><span className="feature-number">02</span><h3>{t('featureTwo')}</h3><p>{t('featureTwoDescription')}</p></article>
        <article className="feature-card"><span className="feature-number">03</span><h3>{t('featureThree')}</h3><p>{t('featureThreeDescription')}</p></article>
      </div>
    </section>
  </>;
}

function LoginPage() {
  const { t } = useI18n();
  return <section className="page-panel container"><div className="panel-card"><span className="panel-icon" aria-hidden="true">◈</span><h1>{t('loginTitle')}</h1><p>{t('loginDescription')}</p><p className="notice" role="status">{t('unavailable')}</p><Link className="button button--secondary" to="/">{t('returnHome')}</Link></div></section>;
}

function DashboardPage() {
  const { t } = useI18n();
  return <section className="page-panel container"><div className="panel-card"><h1>{t('dashboardTitle')}</h1><p>{t('dashboardDescription')}</p></div></section>;
}

function NotFoundPage() {
  const { t } = useI18n();
  return <section className="page-panel container"><div className="panel-card"><h1>{t('pageNotFound')}</h1><p>{t('pageNotFoundDescription')}</p><Link className="button button--secondary" to="/">{t('returnHome')}</Link></div></section>;
}

export function App() {
  return <AppShell><Routes>
    <Route path="/" element={<HomePage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/app" element={<DashboardPage />} />
    </Route>
    <Route path="*" element={<NotFoundPage />} />
  </Routes></AppShell>;
}
