import {createNativeStackNavigator, NativeStackScreenProps} from '@react-navigation/native-stack';
import React from 'react';
import ApiStateView from '../../components/ApiStateView';
import RecipientDetailScreen from './RecipientDetailScreen';
import RecipientsScreen from './RecipientsScreen';
import {Recipient} from '../../types/api';

export type RecipientsStackParamList = {
  RecipientsList: undefined;
  RecipientDetail: {recipientId: string};
};

const Stack = createNativeStackNavigator<RecipientsStackParamList>();

type ListProps = NativeStackScreenProps<RecipientsStackParamList, 'RecipientsList'>;
type DetailProps = NativeStackScreenProps<RecipientsStackParamList, 'RecipientDetail'>;

export default function RecipientsStack({
  recipients,
}: {
  recipients: Recipient[];
}): React.JSX.Element {
  const renderList = ({navigation}: ListProps): React.JSX.Element => (
    <RecipientsScreen
      recipients={recipients}
      onSelect={r => navigation.navigate('RecipientDetail', {recipientId: r.id})}
    />
  );
  const renderDetail = ({navigation, route}: DetailProps): React.JSX.Element => {
    const recipient = recipients.find(r => r.id === route.params.recipientId);
    if (!recipient) {
      return <ApiStateView state="empty" />;
    }
    return <RecipientDetailScreen recipient={recipient} onBack={() => navigation.goBack()} />;
  };
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      <Stack.Screen name="RecipientsList">{renderList}</Stack.Screen>
      <Stack.Screen name="RecipientDetail">{renderDetail}</Stack.Screen>
    </Stack.Navigator>
  );
}
