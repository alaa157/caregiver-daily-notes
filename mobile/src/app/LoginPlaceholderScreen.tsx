import React from 'react';
import {View} from 'react-native';
import AppText from '../components/AppText';

// Placeholder until Task A6 wires real auth gating.
export default function LoginPlaceholderScreen(): React.JSX.Element {
  return (
    <View testID="login-placeholder">
      <AppText>Login</AppText>
    </View>
  );
}
