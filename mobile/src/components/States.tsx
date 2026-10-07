import React from 'react';
import {Button, Text, View} from 'react-native';
import {t} from '../i18n/i18n';

export function LoadingState(): React.JSX.Element {
  return (
    <View testID="loading-state">
      <Text>{t('common.loading')}</Text>
    </View>
  );
}

export function EmptyState({message}: {message?: string}): React.JSX.Element {
  return (
    <View testID="empty-state">
      <Text>{message ?? t('common.empty')}</Text>
    </View>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}): React.JSX.Element {
  return (
    <View testID="error-state">
      <Text testID="error-message">{message}</Text>
      {onRetry ? (
        <Button testID="error-retry" title={t('common.retry')} onPress={onRetry} />
      ) : null}
    </View>
  );
}
