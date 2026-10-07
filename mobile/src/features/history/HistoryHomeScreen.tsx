import React from 'react';
import {View} from 'react-native';
import AppText from '../../components/AppText';

export default function HistoryHomeScreen(): React.JSX.Element {
  return (
    <View testID="history-home">
      <AppText>History</AppText>
    </View>
  );
}
