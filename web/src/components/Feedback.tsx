import { useEffect, type ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { Button } from './Button';
import { Icon } from './Icon';
import { useStatusAnnouncer } from './StatusAnnouncer';

type FeedbackVariant = 'loading' | 'empty' | 'error';

function FeedbackFrame({ variant, children }: { variant: FeedbackVariant; children: ReactNode }) {
  return (
    <div
      className={`feedback feedback--${variant}`}
      role={variant === 'error' ? 'alert' : undefined}
    >
      <span className="feedback-icon" aria-hidden="true">
        {variant === 'loading' ? (
          <span className="spinner" />
        ) : (
          <Icon name={variant === 'empty' ? 'inbox' : 'alert-circle'} size={24} />
        )}
      </span>
      <div className="feedback-content">{children}</div>
    </div>
  );
}

export function LoadingState({ label }: { label?: string }) {
  const { t } = useI18n();
  const announce = useStatusAnnouncer();
  const message = label ?? t('loading');
  useEffect(() => { announce(message); }, [announce, message]);
  return (
    <FeedbackFrame variant="loading">
      <span className="feedback-label">{message}</span>
    </FeedbackFrame>
  );
}

export function EmptyState({ title, description }: { title?: string; description?: string }) {
  const { t } = useI18n();
  const titleText = title ?? t('emptyTitle');
  const descriptionText = description ?? t('emptyDescription');
  const announce = useStatusAnnouncer();
  useEffect(() => { announce(`${titleText}. ${descriptionText}`); }, [announce, titleText, descriptionText]);
  return (
    <FeedbackFrame variant="empty">
      <strong className="feedback-title">{titleText}</strong>
      <p className="feedback-description">{descriptionText}</p>
    </FeedbackFrame>
  );
}

export function ErrorState({ title, description, onRetry }: { title?: string; description?: string; onRetry?: () => void }) {
  const { t } = useI18n();
  return (
    <FeedbackFrame variant="error">
      <strong className="feedback-title">{title ?? t('errorTitle')}</strong>
      <p className="feedback-description">{description ?? t('errorDescription')}</p>
      {onRetry && (
        <Button type="button" variant="secondary" className="feedback-retry" onClick={onRetry}>
          <Icon name="refresh" size={16} />
          {t('retry')}
        </Button>
      )}
    </FeedbackFrame>
  );
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  const { t } = useI18n();
  return (
    <p id={id} className="field-error" role="alert">
      <Icon name="alert-circle" size={16} />
      {message ?? t('required')}
    </p>
  );
}
