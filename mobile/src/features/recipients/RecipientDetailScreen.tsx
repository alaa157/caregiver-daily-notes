import React from 'react';
import {Text, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import {ApiError} from '../../lib/errors';
import {Recipient} from '../../types/api';

export default function RecipientDetailScreen({
  recipient,
}: {
  recipient: Recipient;
}): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View testID="recipient-detail">
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
