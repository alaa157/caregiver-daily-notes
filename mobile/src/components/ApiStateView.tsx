import React from 'react';
import {Button, Text, View} from 'react-native';
import {t} from '../i18n/i18n';
import {ApiError} from '../lib/errors';

export type ApiViewState = 'loading' | 'content' | 'error' | 'empty';

const RETRYABLE: ReadonlySet<string> = new Set(['NETWORK', 'SERVER', 'UNKNOWN']);

function copyFor(error: ApiError): string {
  switch (error.code) {
    case 'NETWORK':
      return t('states.networkError');
    case 'SERVER':
      return t('states.serverError');
    case 'UNAUTHORIZED':
      return t('states.unauthorized');
    case 'AI_UNAVAILABLE':
      return t('states.aiUnavailable');
    default:
      return error.message;
  }
}

/** Unified data-screen states. Retry is offered only when retrying can help. */
export default function ApiStateView({
  state,
  error,
  onRetry,
  children,
}: {
  state: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
  children?: React.ReactNode;
}): React.JSX.Element {
  if (state === 'loading') {
    return (
      <View testID="api-loading">
        <Text>{t('common.loading')}</Text>
      </View>
    );
  }
  if (state === 'empty') {
    return (
      <View testID="api-empty">
        <Text>{t('common.empty')}</Text>
      </View>
    );
  }
  if (state === 'error' && error) {
    const retryable = RETRYABLE.has(error.code) && onRetry !== undefined;
    return (
      <View testID="api-error">
        <Text testID="api-error-message">{copyFor(error)}</Text>
        {retryable ? (
          <Button testID="api-retry" title={t('common.retry')} onPress={onRetry} />
        ) : null}
      </View>
    );
  }
  return <View testID="api-content">{children}</View>;
}
