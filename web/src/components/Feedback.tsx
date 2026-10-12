import { useI18n } from '../i18n/I18nProvider';

export function LoadingState({ label }: { label?: string }) {
  const { t } = useI18n();
  return <div className="feedback" role="status" aria-live="polite"><span className="spinner" aria-hidden="true" />{label ?? t('loading')}</div>;
}

export function EmptyState({ title, description }: { title?: string; description?: string }) {
  const { t } = useI18n();
  return <div className="feedback" role="status"><strong>{title ?? t('emptyTitle')}</strong><p>{description ?? t('emptyDescription')}</p></div>;
}

export function ErrorState({ title, description, onRetry }: { title?: string; description?: string; onRetry?: () => void }) {
  const { t } = useI18n();
  return <div className="feedback" role="alert"><strong>{title ?? t('errorTitle')}</strong><p>{description ?? t('errorDescription')}</p>{onRetry && <button className="button button--secondary" onClick={onRetry}>{t('retry')}</button>}</div>;
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  const { t } = useI18n();
  return <p id={id} className="field-error" role="alert">{message ?? t('required')}</p>;
}
