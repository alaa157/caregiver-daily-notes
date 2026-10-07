import React from 'react';
import {View} from 'react-native';
import {useTranslation} from 'react-i18next';
import AppText from '../../components/AppText';

export default function HistoryHomeScreen(): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View testID="history-home">
      <AppText>{t('tabs.history')}</AppText>
    </View>
  );
}
