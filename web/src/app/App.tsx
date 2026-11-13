import { lazy, Suspense } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { LandingRoute } from '../routes/LandingRoute';
import { AppLayout, AppShell } from '../components/AppShell';
import { ButtonLink } from '../components/Button';
import { LoadingState } from '../components/Feedback';
import { Icon } from '../components/Icon';
import { useI18n } from '../i18n/I18nProvider';
import { PagePanel } from '../components/PagePanel';
import { ProtectedRoute } from './ProtectedRoute';
import { ThemeProvider } from './ThemeProvider';

const LoginPage = lazy(() => import('../features/auth/LoginPage').then(m => ({ default: m.LoginPage })));
const RecipientsRoutes = lazy(() => import('../routes/RecipientsRoutes').then(m => ({ default: m.RecipientsRoutes })));
const NotesRoutes = lazy(() => import('../routes/NotesRoutes').then(m => ({ default: m.NotesRoutes })));
const CreateNotePage = lazy(() => import('../features/notes/NotesPages').then(m => ({ default: m.CreateNotePage })));
const WorkspaceOverview = lazy(() => import('../features/overview/WorkspaceOverview').then(m => ({ default: m.WorkspaceOverview })));
const SettingsPage = lazy(() => import('../features/settings/SettingsPage').then(m => ({ default: m.SettingsPage })));

function NotFoundPage() {
  const { t } = useI18n();
  return (
    <PagePanel icon="leaf" eyebrow={t('notFoundLabel')} title={t('pageNotFound')} description={t('pageNotFoundDescription')}>
      <ButtonLink variant="secondary" to="/"><Icon name="arrow-left" className="directional-icon" />{t('returnHome')}</ButtonLink>
    </PagePanel>
  );
}

export function App() {
  const location = useLocation();
  const backgroundLocation = (location.state as { backgroundLocation?: typeof location } | null)?.backgroundLocation;
  const isLanding = (backgroundLocation ?? location).pathname === '/';
  return (
    <ThemeProvider><Suspense fallback={<LoadingState />}>
      {isLanding ? <LandingRoute /> : <AppShell>
        <Routes location={backgroundLocation ?? location}>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/app" element={<WorkspaceOverview />} />
              <Route path="/app/settings" element={<SettingsPage />} />
              <Route path="/app/recipients/*" element={<RecipientsRoutes />} />
              <Route path="/app/notes/*" element={<NotesRoutes />} />
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        {backgroundLocation && <Routes location={location}>
          <Route element={<ProtectedRoute />}>
            <Route path="/app/notes/new" element={<CreateNotePage />} />
          </Route>
        </Routes>}
      </AppShell>}
    </Suspense></ThemeProvider>
  );
}
