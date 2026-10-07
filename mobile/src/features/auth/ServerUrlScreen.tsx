import React, {useEffect, useState} from 'react';
import {Button, Text, TextInput, View} from 'react-native';
import {t} from '../../i18n/i18n';
import {InvalidServerUrlError, ServerUrlStore} from '../../lib/ServerUrlStore';

// Server URL editor. Reachable from the More tab; the login-screen footer
// link lands with Task A6's LoginScreen.
export default function ServerUrlScreen(): React.JSX.Element {
  const [current, setCurrent] = useState<string>('');
  const [draft, setDraft] = useState<string>('');
  const [saved, setSaved] = useState<boolean>(false);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    let live = true;
    ServerUrlStore.get().then(url => {
      if (live) {
        setCurrent(url);
        setDraft(url);
      }
    });
    return () => {
      live = false;
    };
  }, []);

  async function onSave(): Promise<void> {
    setSaved(false);
    setError(false);
    try {
      await ServerUrlStore.set(draft);
      setCurrent(await ServerUrlStore.get());
      setSaved(true);
    } catch (e) {
      if (e instanceof InvalidServerUrlError) {
        setError(true);
      } else {
        throw e;
      }
    }
  }

  return (
    <View testID="server-url-screen">
      <Text testID="server-url-current">{current}</Text>
      <TextInput
        testID="server-url-input"
        value={draft}
        onChangeText={text => {
          setDraft(text);
          setSaved(false);
          setError(false);
        }}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
      />
      <Button testID="server-url-save" title={t('common.save')} onPress={onSave} />
      {saved ? <Text testID="server-url-saved">{t('settings.serverUrlSaved')}</Text> : null}
      {error ? <Text testID="server-url-error">{t('settings.serverUrlInvalid')}</Text> : null}
    </View>
  );
}
