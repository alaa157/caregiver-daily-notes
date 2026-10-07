import {useNavigation} from '@react-navigation/native';
import React, {useState} from 'react';
import {Button, Text, TextInput, View} from 'react-native';
import {t} from '../../i18n/i18n';
import {ApiError} from '../../lib/errors';
import {useAuth} from './AuthContext';

function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export default function RegisterScreen(): React.JSX.Element {
  const navigation = useNavigation<any>();
  const {signUp} = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
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
    if (password.length < 8) {
      setError(t('auth.errors.passwordTooShort'));
      return;
    }
    if (confirm !== password) {
      setError(t('auth.errors.passwordMismatch'));
      return;
    }
    setBusy(true);
    try {
      await signUp(email.trim(), password);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION') {
        setError(e.message);
      } else {
        setError(t('states.networkError'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <View testID="register-screen">
      <Text testID="register-title">{t('auth.register')}</Text>
      <TextInput
        testID="register-email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="email-address"
      />
      <TextInput
        testID="register-password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      <TextInput
        testID="register-confirm"
        value={confirm}
        onChangeText={setConfirm}
        secureTextEntry
      />
      {error ? <Text testID="register-error">{error}</Text> : null}
      <Button
        testID="register-submit"
        title={t('auth.submit')}
        onPress={onSubmit}
        disabled={busy}
      />
      <Button
        testID="register-login-link"
        title={t('auth.login')}
        onPress={() => navigation.navigate('Login')}
      />
    </View>
  );
}
