import React, {useEffect, useState} from 'react';
import {ActivityIndicator, View} from 'react-native';
import GatedRoot from './src/app/GatedRoot';
import {AuthProvider} from './src/features/auth/AuthContext';
import {loadSavedLanguage} from './src/i18n/i18n';
import {createPartAClient} from './src/app/partAClient';

// Part A runs backend-less: the injected client is FakeTransport-backed with
// scripted fixture responses, so login/register and all feature flows work
// with no backend. Part B (Task B1) rebinds this to
// `new ApiClient(new FetchTransport())`; the FetchTransport class stays in
// lib for that phase.
const partAClient = createPartAClient();

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
        <AuthProvider client={partAClient}>
          <GatedRoot />
        </AuthProvider>
      ) : (
        <ActivityIndicator testID="language-loading" />
      )}
    </View>
  );
}
