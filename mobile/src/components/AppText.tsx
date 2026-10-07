import React from 'react';
import {Text, View} from 'react-native';

// Minimal RTL-safe text primitive. Full theming arrives with feature tasks.
export default function AppText({children}: {children: React.ReactNode}): React.JSX.Element {
  return (
    <View>
      <Text>{children}</Text>
    </View>
  );
}
