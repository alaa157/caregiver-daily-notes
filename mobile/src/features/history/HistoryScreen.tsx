import React, {useState} from 'react';
import {Text, View} from 'react-native';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import AppTextInput from '../../components/AppTextInput';
import {ApiError} from '../../lib/errors';
import {Note} from '../../types/api';

export interface HistoryScreenProps {
  notes: Note[];
  recipientIds: string[];
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
}

export default function HistoryScreen({
  notes,
  recipientIds,
  state = 'content',
  error = null,
  onRetry,
}: HistoryScreenProps): React.JSX.Element {
  // recipientIds feeds the recipient picker in Part B; filtering is free-text until then.
  void recipientIds;
  const [recipientFilter, setRecipientFilter] = useState('');
  const [fromFilter, setFromFilter] = useState('');
  const [toFilter, setToFilter] = useState('');

  const visible = notes.filter(n => {
    if (recipientFilter.trim().length > 0 && n.recipientId !== recipientFilter.trim()) {
      return false;
    }
    if (fromFilter.trim().length > 0 && n.date < fromFilter.trim()) {
      return false;
    }
    if (toFilter.trim().length > 0 && n.date > toFilter.trim()) {
      return false;
    }
    return true;
  });

  const content = (
    <View testID="history-screen">
      <AppTextInput
        testID="history-recipient-filter"
        value={recipientFilter}
        onChangeText={setRecipientFilter}
      />
      <AppTextInput testID="history-date-from" value={fromFilter} onChangeText={setFromFilter} />
      <AppTextInput testID="history-date-to" value={toFilter} onChangeText={setToFilter} />
      {visible.map(n => (
        <View key={n.id} testID="history-note">
          <AppText>{n.text}</AppText>
          <Text testID="history-note-date">{n.date}</Text>
        </View>
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
