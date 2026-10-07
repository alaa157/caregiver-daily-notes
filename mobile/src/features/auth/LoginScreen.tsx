import {useNavigation} from '@react-navigation/native';
import React, {useState} from 'react';
import {Button, Text, TextInput, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import {isRtlRestartPending} from '../../i18n/i18n';
import {ApiError} from '../../lib/errors';
import {useAuth} from './AuthContext';

function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export default function LoginScreen(): React.JSX.Element {
  const navigation = useNavigation<any>();
  const {signIn} = useAuth();
  const {t} = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(): Promise<void> {
    setError(null);
    if (email.trim().length === 0) {
      setError(t('auth.errors.emailRequired'));
      return;
    }
    if (!isEmail(email)) {
      setError(t('auth.errors.emailInvalid'));
      return;
    }
    if (password.length === 0) {
      setError(t('auth.errors.passwordRequired'));
      return;
    }
    if (password.length < 8) {
      setError(t('auth.errors.passwordTooShort'));
      return;
    }
    setBusy(true);
    try {
      await signIn(email.trim(), password);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'UNAUTHORIZED') {
        setError(t('auth.errors.invalidCredentials'));
      } else if (e instanceof ApiError && e.code === 'SERVER') {
        setError(t('states.serverError'));
      } else {
        setError(t('states.networkError'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <View testID="login-screen">
      <Text testID="login-title">{t('auth.login')}</Text>
      <TextInput
        testID="login-email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="email-address"
      />
      <TextInput
        testID="login-password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      {error ? <Text testID="login-error">{error}</Text> : null}
      {isRtlRestartPending() ? (
        <Text testID="rtl-restart-notice">{t('common.rtlRestartRequired')}</Text>
      ) : null}
      <Button testID="login-submit" title={t('auth.submit')} onPress={onSubmit} disabled={busy} />
      <Button
        testID="login-register-link"
        title={t('auth.register')}
        onPress={() => navigation.navigate('Register')}
      />
      <Button
        testID="login-server-url-link"
        title={t('settings.serverUrl')}
        onPress={() => navigation.navigate('ServerUrl')}
      />
    </View>
  );
}
