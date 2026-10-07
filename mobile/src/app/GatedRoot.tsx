import React from 'react';
import {View} from 'react-native';
import AppText from '../components/AppText';
import {useAuth} from '../features/auth/AuthContext';
import {t} from '../i18n/i18n';
import RootNavigator from './RootNavigator';

export default function GatedRoot(): React.JSX.Element {
  const {token, loading} = useAuth();
  if (loading) {
    return (
      <View testID="auth-loading">
        <AppText>{t('common.loading')}</AppText>
      </View>
    );
  }
  return <RootNavigator signedIn={token !== null} />;
}
