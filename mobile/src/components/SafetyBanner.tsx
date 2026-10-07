import React from 'react';
import {Text, View} from 'react-native';

// Deterministic safety banner. Intentionally no dismiss/close control of any
// kind: when flags exist, the banner stays visible.
export default function SafetyBanner({flags}: {flags: string[]}): React.JSX.Element | null {
  if (flags.length === 0) {
    return null;
  }
  return (
    <View testID="safety-banner">
      <Text testID="safety-banner-text">{flags.join(', ')}</Text>
    </View>
  );
}
