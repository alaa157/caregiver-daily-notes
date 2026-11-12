import { Route, Routes, useLocation } from 'react-router-dom';
import { LandingRoute } from '../routes/LandingRoute';
import { AppLayout, AppShell } from '../components/AppShell';
import { ButtonLink } from '../components/Button';
import { Icon } from '../components/Icon';
import { useI18n } from '../i18n/I18nProvider';
import { PagePanel } from '../components/PagePanel';
import { LoginPage } from '../features/auth/LoginPage';
import { ProtectedRoute } from './ProtectedRoute';
import { RecipientsRoutes } from '../routes/RecipientsRoutes';
import { NotesRoutes } from '../routes/NotesRoutes';
import { CreateNotePage } from '../features/notes/NotesPages';
import { WorkspaceOverview } from '../features/overview/WorkspaceOverview';
import { SettingsPage } from '../features/settings/SettingsPage';
import { ThemeProvider } from './ThemeProvider';

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
  return (
    <ThemeProvider><AppShell>
      <Routes location={backgroundLocation ?? location}>
        <Route path="/" element={<LandingRoute />} />
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
    </AppShell></ThemeProvider>
  );
}
