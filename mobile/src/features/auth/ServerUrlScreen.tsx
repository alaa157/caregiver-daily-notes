import React, {useEffect, useState} from 'react';
import {Button, Text, TextInput, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import {InvalidServerUrlError, ServerUrlStore} from '../../lib/ServerUrlStore';

// Server URL editor. Reachable from the More tab; the login-screen footer
// link lands with Task A6's LoginScreen.
export default function ServerUrlScreen(): React.JSX.Element {
  const {t} = useTranslation();
  const [current, setCurrent] = useState<string>('');
  const [draft, setDraft] = useState<string>('');
  const [saved, setSaved] = useState<boolean>(false);
  const [resetDone, setResetDone] = useState<boolean>(false);
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
    setResetDone(false);
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

  async function onReset(): Promise<void> {
    setSaved(false);
    setResetDone(false);
    setError(false);
    await ServerUrlStore.reset();
    const url = await ServerUrlStore.get();
    setCurrent(url);
    setDraft(url);
    setResetDone(true);
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
          setResetDone(false);
          setError(false);
        }}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
      />
      <Button testID="server-url-save" title={t('common.save')} onPress={onSave} />
      <Button testID="server-url-reset" title={t('settings.resetServerUrl')} onPress={onReset} />
      {saved ? <Text testID="server-url-saved">{t('settings.serverUrlSaved')}</Text> : null}
      {resetDone ? (
        <Text testID="server-url-reset-done">{t('settings.serverUrlReset')}</Text>
      ) : null}
      {error ? <Text testID="server-url-error">{t('settings.serverUrlInvalid')}</Text> : null}
    </View>
  );
}
