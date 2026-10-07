import React from 'react';
import {View} from 'react-native';
import AppText from '../components/AppText';

export default function MoreHomeScreen(): React.JSX.Element {
  return (
    <View testID="more-home">
      <AppText>More</AppText>
    </View>
  );
}
