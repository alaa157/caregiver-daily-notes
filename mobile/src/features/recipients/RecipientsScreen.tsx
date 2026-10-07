import React from 'react';
import {Pressable, View} from 'react-native';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import {ApiError} from '../../lib/errors';
import {Recipient} from '../../types/api';

export interface RecipientsScreenProps {
  recipients: Recipient[];
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
  onSelect?: (recipient: Recipient) => void;
}

export default function RecipientsScreen({
  recipients,
  state = 'content',
  error = null,
  onRetry,
  onSelect,
}: RecipientsScreenProps): React.JSX.Element {
  const content = (
    <View testID="recipients-list">
      {recipients.map(r => (
        <Pressable
          key={r.id}
          testID={`recipient-row-${r.id}`}
          accessibilityRole="button"
          onPress={() => onSelect?.(r)}>
          <AppText>{r.name}</AppText>
        </Pressable>
      ))}
    </View>
  );
  if (state === 'error') {
    return (
      <ApiStateView state="error" error={error} onRetry={onRetry}>
        {content}
      </ApiStateView>
    );
  }
  return (
    <ApiStateView state={state} onRetry={onRetry}>
      {content}
    </ApiStateView>
  );
}
