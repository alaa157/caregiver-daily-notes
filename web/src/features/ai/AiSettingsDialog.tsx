import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { useApiClient } from '../../lib/api';
import { useByok, type ByokProviderId } from './ByokContext';
import { loadAiPreferences, saveAiPreferences } from './aiPreferences';
import { MODEL_CATALOG, MODEL_ID_PATTERN, requiresDataUseAck } from './modelCatalog';
import './ai-settings.css';

const providers: { id: ByokProviderId; label: MessageKey }[] = [
  { id: 'OPENAI', label: 'byokProviderOpenAi' },
  { id: 'GEMINI', label: 'byokProviderGemini' },
  { id: 'XAI', label: 'byokProviderXai' },
  { id: 'OPEN_ROUTER', label: 'byokProviderOpenRouter' },
];

const providerNotices: Record<ByokProviderId, MessageKey> = {
  GEMINI: 'byokNoticeGemini',
  OPENAI: 'byokNoticeOpenAi',
  XAI: 'byokNoticeXai',
  OPEN_ROUTER: 'byokNoticeOpenRouter',
};

const errorMessages: Record<string, MessageKey> = {
  invalid_key: 'byokErrorInvalidKey',
  invalid_model: 'byokErrorInvalidModel',
  rate_limit: 'byokErrorRateLimit',
  provider_unavailable: 'byokErrorProviderUnavailable',
  invalid_provider: 'byokErrorInvalidProvider',
};

type ConnectionTestState =
  | { kind: 'idle' }
  | { kind: 'testing' }
  | { kind: 'succeeded' }
  | { kind: 'failed'; message: MessageKey };

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
  const { credentials, setCredentials, clearCredentials } = useByok();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const providerRef = useRef<HTMLSelectElement>(null);
  const [providerId, setProviderId] = useState<ByokProviderId>('GEMINI');
  const [modelChoice, setModelChoice] = useState('');
  const [customModel, setCustomModel] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [dataUseAck, setDataUseAck] = useState(false);
  const [testState, setTestState] = useState<ConnectionTestState>({ kind: 'idle' });
  const pending = testState.kind === 'testing';
  const modelOptions = MODEL_CATALOG[providerId];
  const otherModelSelected = modelChoice === 'other';
  const customModelValid = MODEL_ID_PATTERN.test(customModel);
  const customModelInvalid = otherModelSelected && customModel.length > 0 && !customModelValid;
  const selectedModel = otherModelSelected ? customModel : modelChoice;
  const modelReady = selectedModel.length > 0 && (!otherModelSelected || customModelValid);
  const ackRequired = requiresDataUseAck(providerId, selectedModel);
  const ackBlocked = ackRequired && !dataUseAck;

  useEffect(() => {
    if (!open) return;
    const preferences = loadAiPreferences();
    const nextProvider = preferences?.providerId ?? 'GEMINI';
    const catalogHasModel = preferences
      ? MODEL_CATALOG[nextProvider].some((option) => option.id === preferences.model)
      : false;
    setProviderId(nextProvider);
    setModelChoice(preferences ? (catalogHasModel ? preferences.model : 'other') : '');
    setCustomModel(preferences && !catalogHasModel ? preferences.model : '');
    setApiKey('');
    setDataUseAck(false);
    setTestState({ kind: 'idle' });
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const dialog = dialogRef.current!;
    const previouslyFocused = document.activeElement;
    dialog.showModal();
    providerRef.current?.focus();
    return () => {
      if (dialog.open) dialog.close();
      if (previouslyFocused instanceof HTMLElement && previouslyFocused.isConnected) previouslyFocused.focus();
    };
  }, [open]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending || !modelReady || ackBlocked) return;
    setTestState({ kind: 'testing' });
    try {
      const response: unknown = await api.request({
        path: '/api/ai/byok/connection-test',
        method: 'POST',
        body: { credentials: { providerId, model: selectedModel, apiKey } },
      });
      if (typeof response === 'object' && response !== null && 'success' in response && response.success === true) {
        setCredentials({ providerId, model: selectedModel, apiKey });
        saveAiPreferences({ providerId, model: selectedModel });
        setApiKey('');
        setTestState({ kind: 'succeeded' });
      } else {
        const code = typeof response === 'object' && response !== null && 'errorCode' in response
          && typeof response.errorCode === 'string' ? response.errorCode : '';
        setTestState({ kind: 'failed', message: errorMessages[code] ?? 'byokErrorProviderUnavailable' });
      }
    } catch (failure) {
      setTestState({ kind: 'failed', message: errorMessages[errorCodeFrom(failure)] ?? 'byokErrorProviderUnavailable' });
    }
  };

  const clearTestStatus = () => {
    setTestState({ kind: 'idle' });
  };

  const removeKey = () => {
    clearCredentials();
    setApiKey('');
    clearTestStatus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDialogElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose?.();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ));
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const focusIsInside = focusable.includes(document.activeElement as HTMLElement);
    if (!first || !last) {
      event.preventDefault();
      event.currentTarget.focus();
    } else if (event.shiftKey && (document.activeElement === first || !focusIsInside)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !focusIsInside)) {
      event.preventDefault();
      first.focus();
    }
  };

  if (!open) return null;

  const activeProvider = credentials && providers.find((provider) => provider.id === credentials.providerId);

  return (
    <dialog
      ref={dialogRef}
      className="byok-dialog"
      aria-modal="true"
      aria-labelledby="byok-dialog-title"
      tabIndex={-1}
      onKeyDown={handleKeyDown}
      onCancel={(event) => {
        event.preventDefault();
        onClose?.();
      }}
    >
      <h2 id="byok-dialog-title">{t('byokDialogTitle')}</h2>
      <section aria-label={t('byokActiveCredentialsLabel')} className="byok-active">
        {credentials && activeProvider
          ? <p><strong>{t('byokActiveCredentialsLabel')}:</strong> {t(activeProvider.label)} · <bdi>{credentials.model}</bdi></p>
          : <p>{t('byokNoActiveCredentials')}</p>}
        <p>{t('byokDraftNotice')}</p>
        <p className="byok-provider-notice">{t('byokKeyMemoryNotice')}</p>
        <p className="byok-provider-notice">{t('byokDataSentNotice')}</p>
      </section>
      <form onSubmit={submit}>
        <div className="auth-field">
          <label htmlFor="byok-provider">{t('byokProviderLabel')}</label>
          <select ref={providerRef} id="byok-provider" value={providerId} disabled={pending}
            onChange={(event) => {
              setProviderId(event.target.value as ByokProviderId);
              setModelChoice('');
              setCustomModel('');
              setDataUseAck(false);
              clearTestStatus();
            }}>
            {providers.map((provider) => <option key={provider.id} value={provider.id}>{t(provider.label)}</option>)}
          </select>
        </div>
        <div className="auth-field">
          <span className="byok-field-label" id="byok-model-label">{t('byokModelLabel')}</span>
          <div className="byok-model-picker" role="radiogroup" aria-labelledby="byok-model-label">
            {modelOptions.map((option) => (
              <label key={option.id} className="byok-model-option">
                <input type="radio" name="byok-model-choice" value={option.id}
                  checked={modelChoice === option.id} disabled={pending}
                  onChange={() => { setModelChoice(option.id); setDataUseAck(false); clearTestStatus(); }} />
                <span className="byok-model-option-body">
                  <span className="byok-model-option-title">
                    <bdi>{option.label}</bdi>
                    <span className="byok-badge">{t(option.badge)}</span>
                  </span>
                  {option.note && <span className="byok-model-option-note">{t(option.note)}</span>}
                </span>
              </label>
            ))}
            <label className="byok-model-option">
              <input type="radio" name="byok-model-choice" value="other"
                checked={otherModelSelected} disabled={pending}
                onChange={() => { setModelChoice('other'); setDataUseAck(false); clearTestStatus(); }} />
              <span className="byok-model-option-body">
                <span className="byok-model-option-title">{t('byokModelOther')}</span>
              </span>
            </label>
          </div>
          {otherModelSelected && (
            <div className="auth-field byok-other-model">
              <label htmlFor="byok-model">{t('byokModelOtherLabel')}</label>
              <input id="byok-model" type="text" autoComplete="off" required maxLength={128}
                value={customModel} readOnly={pending}
                aria-invalid={customModelInvalid || undefined}
                aria-describedby={customModelInvalid ? 'byok-model-error' : undefined}
                onChange={(event) => { setCustomModel(event.target.value); setDataUseAck(false); clearTestStatus(); }} />
              {customModelInvalid && <p id="byok-model-error" className="byok-model-error">{t('byokModelIdInvalid')}</p>}
            </div>
          )}
          <p className="byok-provider-notice">{t(providerNotices[providerId])}</p>
          {ackRequired && (
            <label className="byok-ack">
              <input type="checkbox" checked={dataUseAck} disabled={pending}
                onChange={(event) => setDataUseAck(event.target.checked)} />
              <span>{t('byokDataUseAck')}</span>
            </label>
          )}
        </div>
        <div className="auth-field">
          <label htmlFor="byok-key">{t('byokKeyLabel')}</label>
          <input id="byok-key" type="password" autoComplete="off" required value={apiKey} readOnly={pending}
            onChange={(event) => { setApiKey(event.target.value); clearTestStatus(); }} />
        </div>
        <div className="byok-actions">
          <Button type="submit" disabled={pending || !modelReady || !apiKey || ackBlocked}>{t(pending ? 'byokTestingConnection' : 'byokTestConnection')}</Button>
          <Button type="button" variant="secondary" disabled={pending || (!credentials && !apiKey)} onClick={removeKey}>{t('byokRemoveKey')}</Button>
        </div>
      </form>
      {testState.kind === 'succeeded' && <p role="status">{t('byokConnectionSuccess')}</p>}
      {testState.kind === 'failed' && (
        <div role="alert">
          <p>{t(testState.message)}</p>
          <p>{t(credentials ? 'byokFailureActiveUnchanged' : 'byokFailureNotActivated')}</p>
        </div>
      )}
      {onClose && <Button type="button" variant="text" onClick={onClose} aria-label={t('byokCloseDialog')}>{t('byokCloseDialog')}</Button>}
    </dialog>
  );
}
