import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, useLocation } from 'react-router-dom';
import { ErrorState, LoadingState } from '../components/Feedback';
import { StatusAnnouncerProvider } from '../components/StatusAnnouncer';
import { I18nProvider, useI18n } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { loadRuntimeConfig, type RuntimeConfig } from '../lib/config';
import { AuthProvider } from './AuthContext';
import { App } from './App';

type ConfigStatus =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'ready'; config: RuntimeConfig };

/**
 * Loads runtime config only while a route that needs the API is active. The
 * public landing route never enables it, so `/` renders immediately even when
 * config.json is slow or missing. State lives above the gate so navigating
 * between API routes does not re-fetch or unmount the authenticated tree.
 */
function useRuntimeConfig(enabled: boolean) {
  const [status, setStatus] = useState<ConfigStatus>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    setStatus({ kind: 'loading' });
    loadRuntimeConfig()
      .then((config) => { if (active) setStatus({ kind: 'ready', config }); })
      .catch(() => { if (active) setStatus({ kind: 'error' }); });
    return () => { active = false; };
  }, [enabled, attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  return { status, retry };
}

function ConfigError({ retry }: { retry: () => void }) {
  const { t } = useI18n();
  return <div className="config-error"><ErrorState title={t('configErrorTitle')} description={t('configErrorDescription')} onRetry={retry} /></div>;
}

/**
 * The only place config is required. Until it resolves this renders the
 * loading state instead of the app, so a protected route can never flash
 * unauthenticated content, and a failure is visible only on API routes.
 */
export function ConfigBoundary({ status, retry, children }: {
  status: ConfigStatus;
  retry: () => void;
  children: ReactNode;
}) {
  if (status.kind === 'loading') return <StatusAnnouncerProvider><LoadingState /></StatusAnnouncerProvider>;
  if (status.kind === 'error') return <ConfigError retry={retry} />;
  return <ApiProvider config={status.config}><AuthProvider>{children}</AuthProvider></ApiProvider>;
}

function ConfiguredRoutes() {
  // The landing page is the only route that renders without the app shell and
  // its config-dependent providers; every other route goes through the gate.
  const isLanding = useLocation().pathname === '/';
  const { status, retry } = useRuntimeConfig(!isLanding);
  return isLanding
    ? <App />
    : <ConfigBoundary status={status} retry={retry}><App /></ConfigBoundary>;
}

export function Bootstrap() {
  return <I18nProvider><BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <ConfiguredRoutes />
  </BrowserRouter></I18nProvider>;
}
