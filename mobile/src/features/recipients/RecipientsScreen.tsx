import React from 'react';
import {View} from 'react-native';
import {Recipient} from '../../types/api';
import AppText from '../../components/AppText';

export default function RecipientsScreen({
  recipients,
}: {
  recipients: Recipient[];
}): React.JSX.Element {
  return (
    <View testID="recipients-list">
      {recipients.map(r => (
        <View key={r.id} testID="recipient-row">
          <AppText>{r.name}</AppText>
        </View>
      ))}
    </View>
  );
}
