import {createNativeStackNavigator} from '@react-navigation/native-stack';
import React, {useState} from 'react';
import RecipientDetailScreen from './RecipientDetailScreen';
import RecipientsScreen from './RecipientsScreen';
import {Recipient} from '../../types/api';

const Stack = createNativeStackNavigator();

export default function RecipientsStack({
  recipients,
}: {
  recipients: Recipient[];
}): React.JSX.Element {
  const [selected, setSelected] = useState<Recipient | null>(null);
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      {selected ? (
        <Stack.Screen name="RecipientDetail">
          {() => <RecipientDetailScreen recipient={selected} />}
        </Stack.Screen>
      ) : (
        <Stack.Screen name="RecipientsList">
          {() => <RecipientsScreen recipients={recipients} onSelect={setSelected} />}
        </Stack.Screen>
      )}
    </Stack.Navigator>
  );
}
