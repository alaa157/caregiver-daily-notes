import React, {useEffect, useState} from 'react';
import {ActivityIndicator, View} from 'react-native';
import {ApiClient} from './src/lib/ApiClient';
import {FetchTransport} from './src/lib/FetchTransport';
import GatedRoot from './src/app/GatedRoot';
import {AuthProvider} from './src/features/auth/AuthContext';
import {loadSavedLanguage} from './src/i18n/i18n';

import {createPartAClient} from './src/app/partAClient';

// PREVIEW DEMO BUILD (preview/full-stack only, never merge to main):
// production path talks to the real backend over the runtime server URL
// (set it from the login screen's Server URL link). Tests set the global
// flag below to keep exercising the scripted Part A client. Delete this
// override when Part B lands properly.
declare global {
  var __USE_PART_A_CLIENT__: boolean | undefined;
}

function createAppClient(): ApiClient {
  if (globalThis.__USE_PART_A_CLIENT__) {
    return createPartAClient();
  }
  return new ApiClient(new FetchTransport());
}

export default function App(): React.JSX.Element {
  const [languageReady, setLanguageReady] = useState(false);
  const client = React.useMemo(createAppClient, []);

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
        <AuthProvider client={client}>
          <GatedRoot />
        </AuthProvider>
      ) : (
        <ActivityIndicator testID="language-loading" />
      )}
    </View>
  );
}
