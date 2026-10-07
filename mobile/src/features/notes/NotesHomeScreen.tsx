import React from 'react';
import {View} from 'react-native';
import AppText from '../../components/AppText';

export default function NotesHomeScreen(): React.JSX.Element {
  return (
    <View testID="notes-home">
      <AppText>Notes</AppText>
    </View>
  );
}
