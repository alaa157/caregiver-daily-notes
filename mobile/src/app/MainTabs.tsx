import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {useTranslation} from 'react-i18next';
import HistoryHomeScreen from '../features/history/HistoryHomeScreen';
import NotesHomeScreen from '../features/notes/NotesHomeScreen';
import PlansHomeScreen from '../features/plans/PlansHomeScreen';
import MoreStack from './MoreStack';

const Tab = createBottomTabNavigator();

export default function MainTabs(): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <Tab.Navigator>
      <Tab.Screen name="Notes" component={NotesHomeScreen} options={{tabBarLabel: t('tabs.notes')}} />
      <Tab.Screen
        name="History"
        component={HistoryHomeScreen}
        options={{tabBarLabel: t('tabs.history')}}
      />
      <Tab.Screen name="Plans" component={PlansHomeScreen} options={{tabBarLabel: t('tabs.plans')}} />
      {/* tabBarButtonTestID is invisible to jest but used by on-device e2e later. */}
      <Tab.Screen
        name="More"
        component={MoreStack}
        options={{tabBarButtonTestID: 'tab-more', tabBarLabel: t('tabs.more')}}
      />
    </Tab.Navigator>
  );
}
