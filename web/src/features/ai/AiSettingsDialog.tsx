import { useState, type FormEvent } from 'react';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { useApiClient } from '../../lib/api';
import { useByok, type ByokProviderId } from './ByokContext';

const providers: { id: ByokProviderId; label: MessageKey }[] = [
  { id: 'OPENAI', label: 'byokProviderOpenAi' },
  { id: 'GEMINI', label: 'byokProviderGemini' },
  { id: 'XAI', label: 'byokProviderXai' },
  { id: 'OPEN_ROUTER', label: 'byokProviderOpenRouter' },
];

const errorMessages: Record<string, MessageKey> = {
  invalid_key: 'byokErrorInvalidKey',
  invalid_model: 'byokErrorInvalidModel',
  rate_limit: 'byokErrorRateLimit',
  provider_unavailable: 'byokErrorProviderUnavailable',
  invalid_provider: 'byokErrorInvalidProvider',
};

function errorCodeFrom(value: unknown): string {
  if (typeof value !== 'object' || value === null || !('details' in value)) return '';
  const details = value.details;
  if (typeof details !== 'object' || details === null) return '';
  if ('errorCode' in details && typeof details.errorCode === 'string') return details.errorCode;
  return '';
}

export function AiSettingsDialog({ open, onClose }: { open: boolean; onClose?: () => void }) {
  const { t } = useI18n();
  const api = useApiClient();
  const { setCredentials, clearCredentials } = useByok();
  const [providerId, setProviderId] = useState<ByokProviderId>('GEMINI');
  const [model, setModel] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [status, setStatus] = useState<MessageKey | null>(null);
  const [pending, setPending] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending) return;
    setStatus(null);
    setPending(true);
    try {
      const response: unknown = await api.request({
        path: '/api/ai/byok/connection-test',
        method: 'POST',
        body: { credentials: { providerId, model, apiKey } },
      });
      if (typeof response === 'object' && response !== null && 'success' in response && response.success === true) {
        setCredentials({ providerId, model, apiKey });
        setStatus('byokConnectionSuccess');
      } else {
        const code = typeof response === 'object' && response !== null && 'errorCode' in response
          && typeof response.errorCode === 'string' ? response.errorCode : '';
        setStatus(errorMessages[code] ?? 'byokErrorProviderUnavailable');
      }
    } catch (failure) {
      setStatus(errorMessages[errorCodeFrom(failure)] ?? 'byokErrorProviderUnavailable');
    } finally {
      setPending(false);
    }
  };

  const removeKey = () => {
    clearCredentials();
    setApiKey('');
    setStatus(null);
  };

  if (!open) return null;

  return (
    <section role="dialog" aria-modal="true" aria-labelledby="byok-dialog-title">
      <h2 id="byok-dialog-title">{t('byokDialogTitle')}</h2>
      <form onSubmit={submit}>
        <div className="auth-field">
          <label htmlFor="byok-provider">{t('byokProviderLabel')}</label>
          <select id="byok-provider" value={providerId} disabled={pending}
            onChange={(event) => setProviderId(event.target.value as ByokProviderId)}>
            {providers.map((provider) => <option key={provider.id} value={provider.id}>{t(provider.label)}</option>)}
          </select>
        </div>
        <div className="auth-field">
          <label htmlFor="byok-model">{t('byokModelLabel')}</label>
          <input id="byok-model" type="text" autoComplete="off" required value={model} readOnly={pending}
            onChange={(event) => setModel(event.target.value)} />
        </div>
        <div className="auth-field">
          <label htmlFor="byok-key">{t('byokKeyLabel')}</label>
          <input id="byok-key" type="password" autoComplete="off" required value={apiKey} readOnly={pending}
            onChange={(event) => setApiKey(event.target.value)} />
        </div>
        <Button type="submit" disabled={pending || !model.trim() || !apiKey}>{t(pending ? 'byokTestingConnection' : 'byokTestConnection')}</Button>
        <Button type="button" variant="secondary" disabled={pending} onClick={removeKey}>{t('byokRemoveKey')}</Button>
      </form>
      {status && <p role={status === 'byokConnectionSuccess' ? 'status' : 'alert'}>{t(status)}</p>}
      {onClose && <Button type="button" variant="text" onClick={onClose} aria-label={t('byokCloseDialog')}>{t('byokCloseDialog')}</Button>}
    </section>
  );
}
