import React, {useState} from 'react';
import {Text, View} from 'react-native';
import AppText from '../../components/AppText';
import AppTextInput from '../../components/AppTextInput';
import {Note} from '../../types/api';

export default function HistoryScreen({
  notes,
  recipientIds,
}: {
  notes: Note[];
  recipientIds: string[];
}): React.JSX.Element {
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

  return (
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
}
