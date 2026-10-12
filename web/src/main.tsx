import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './app/App';
import { AuthProvider } from './app/AuthContext';
import { ErrorState, LoadingState } from './components/Feedback';
import { I18nProvider, useI18n } from './i18n/I18nProvider';
import { ApiProvider } from './lib/api';
import { loadRuntimeConfig, type RuntimeConfig } from './lib/config';
import '@fontsource-variable/dm-sans';
import '@fontsource-variable/noto-sans-arabic';
import './styles/base.css';

type BootstrapStatus =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'ready'; config: RuntimeConfig };

function Bootstrap() {
  const [status, setStatus] = useState<BootstrapStatus>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    loadRuntimeConfig()
      .then((config) => { if (active) setStatus({ kind: 'ready', config }); })
      .catch(() => { if (active) setStatus({ kind: 'error' }); });
    return () => { active = false; };
  }, [attempt]);

  return <I18nProvider>
    {status.kind === 'loading' && <LoadingState />}
    {status.kind === 'error' && <ConfigError retry={() => { setStatus({ kind: 'loading' }); setAttempt((value) => value + 1); }} />}
    {status.kind === 'ready' && <ApiProvider config={status.config}><AuthProvider><BrowserRouter><App /></BrowserRouter></AuthProvider></ApiProvider>}
  </I18nProvider>;
}

function ConfigError({ retry }: { retry: () => void }) {
  const { t } = useI18n();
  return <div className="config-error"><ErrorState title={t('configErrorTitle')} description={t('configErrorDescription')} onRetry={retry} /></div>;
}

const root = document.getElementById('root');
if (!root) throw new Error('Missing root element');
createRoot(root).render(<StrictMode><Bootstrap /></StrictMode>);
