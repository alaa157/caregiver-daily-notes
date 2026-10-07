import React from 'react';
import {View} from 'react-native';
import AppText from '../../components/AppText';

export default function PlansHomeScreen(): React.JSX.Element {
  return (
    <View testID="plans-home">
      <AppText>Plans</AppText>
    </View>
  );
}
