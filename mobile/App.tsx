import React, {useEffect, useState} from 'react';
import {ActivityIndicator, View} from 'react-native';
import {ApiClient} from './src/lib/ApiClient';
import {FetchTransport} from './src/lib/FetchTransport';
import GatedRoot from './src/app/GatedRoot';
import {AuthProvider} from './src/features/auth/AuthContext';
import {loadSavedLanguage} from './src/i18n/i18n';

const prodClient = new ApiClient(new FetchTransport());

export default function App(): React.JSX.Element {
  const [languageReady, setLanguageReady] = useState(false);

  useEffect(() => {
    let live = true;
    void loadSavedLanguage()
      .catch(() => {})
      .finally(() => {
        if (live) {
          setLanguageReady(true);
        }
      });
    return () => {
      live = false;
    };
  }, []);

  return (
    <View testID="app-root" style={{flex: 1}}>
      {languageReady ? (
        <AuthProvider client={prodClient}>
          <GatedRoot />
        </AuthProvider>
      ) : (
        <ActivityIndicator testID="language-loading" />
      )}
    </View>
  );
}
