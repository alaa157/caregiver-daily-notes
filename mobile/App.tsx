import React from 'react';
import {View} from 'react-native';
import RootNavigator from './src/app/RootNavigator';

export default function App(): React.JSX.Element {
  return (
    <View testID="app-root" style={{flex: 1}}>
      <RootNavigator signedIn={false} />
    </View>
  );
}
