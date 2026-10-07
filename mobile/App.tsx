import React from 'react';
import {View} from 'react-native';
import {ApiClient} from './src/lib/ApiClient';
import {FetchTransport} from './src/lib/FetchTransport';
import GatedRoot from './src/app/GatedRoot';
import {AuthProvider} from './src/features/auth/AuthContext';

const prodClient = new ApiClient(new FetchTransport());

export default function App(): React.JSX.Element {
  return (
    <View testID="app-root" style={{flex: 1}}>
      <AuthProvider client={prodClient}>
        <GatedRoot />
      </AuthProvider>
    </View>
  );
}
