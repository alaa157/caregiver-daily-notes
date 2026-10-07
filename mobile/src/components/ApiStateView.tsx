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

type BaseProps = {
  onRetry?: () => void;
  children?: React.ReactNode;
};

/**
 * Unified data-screen states. The discriminated union requires an error in
 * the error state; if one is somehow missing at runtime, a safe generic
 * error renders instead of the content. Retry is offered only when
 * retrying can help.
 */
export type ApiStateViewProps =
  | (BaseProps & {state: 'loading' | 'content' | 'empty'})
  | (BaseProps & {state: 'error'; error: ApiError | null});

function isErrorProps(props: ApiStateViewProps): props is BaseProps & {
  state: 'error';
  error: ApiError | null;
} {
  return props.state === 'error';
}

/** Safe generic error for the missing-error case. Never offers retry. */
function MissingErrorState(): React.JSX.Element {
  return (
    <View testID="api-error">
      <Text testID="api-error-message">{t('states.serverError')}</Text>
    </View>
  );
}

export default function ApiStateView(props: ApiStateViewProps): React.JSX.Element {
  const {state, onRetry, children} = props;
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
  if (isErrorProps(props)) {
    if (!props.error) {
      return <MissingErrorState />;
    }
    const error = props.error;
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
