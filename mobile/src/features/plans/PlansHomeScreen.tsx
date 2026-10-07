import React from 'react';
import {View} from 'react-native';
import {useTranslation} from 'react-i18next';
import AppText from '../../components/AppText';

export default function PlansHomeScreen(): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View testID="plans-home">
      <AppText>{t('tabs.plans')}</AppText>
    </View>
  );
}
