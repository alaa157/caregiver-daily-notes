import React from 'react';
import {View} from 'react-native';
import {useTranslation} from 'react-i18next';
import AppText from '../../components/AppText';

export default function NotesHomeScreen(): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View testID="notes-home">
      <AppText>{t('tabs.notes')}</AppText>
    </View>
  );
}
