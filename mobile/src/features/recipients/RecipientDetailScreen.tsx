import React from 'react';
import {Button, Text, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import AppText from '../../components/AppText';
import {Recipient} from '../../types/api';

export default function RecipientDetailScreen({
  recipient,
  onBack,
}: {
  recipient: Recipient;
  onBack?: () => void;
}): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View testID="recipient-detail">
      {onBack ? (
        <Button testID="recipient-detail-back" title={t('common.back')} onPress={onBack} />
      ) : null}
      <Text testID="recipient-detail-name">{recipient.name}</Text>
      <AppText>{t('recipients.status')}</AppText>
      <Text testID="recipient-detail-status">
        {t(recipient.active ? 'recipients.active' : 'recipients.inactive')}
      </Text>
    </View>
  );
}

export function recipientDetailTitle(recipient: Recipient): string {
  return recipient.name;
}
