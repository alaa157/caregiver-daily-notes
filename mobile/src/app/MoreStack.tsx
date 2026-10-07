import {useNavigation} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import React from 'react';
import {Button, View} from 'react-native';
import AppText from '../components/AppText';
import {useAuth} from '../features/auth/AuthContext';
import ServerUrlScreen from '../features/auth/ServerUrlScreen';
import {t} from '../i18n/i18n';

const Stack = createNativeStackNavigator();

function MoreHome(): React.JSX.Element {
  const navigation = useNavigation<any>();
  const {signOut} = useAuth();
  return (
    <View testID="more-home">
      <AppText>More</AppText>
      <Button
        testID="more-settings-button"
        title={t('settings.title')}
        onPress={() => navigation.navigate('ServerUrl')}
      />
      <Button testID="more-signout-button" title={t('auth.logout')} onPress={signOut} />
    </View>
  );
}

export default function MoreStack(): React.JSX.Element {
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      <Stack.Screen name="MoreHome" component={MoreHome} />
      <Stack.Screen name="ServerUrl" component={ServerUrlScreen} />
    </Stack.Navigator>
  );
}
