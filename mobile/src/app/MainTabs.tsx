import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import {useTranslation} from 'react-i18next';
import HistoryContainer from '../features/history/HistoryContainer';
import NotesStack from '../features/notes/NotesStack';
import PlansStack from '../features/plans/PlansStack';
import MoreStack from './MoreStack';

const Tab = createBottomTabNavigator();

export default function MainTabs(): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <Tab.Navigator>
      <Tab.Screen name="Notes" component={NotesStack} options={{tabBarLabel: t('tabs.notes')}} />
      <Tab.Screen
        name="History"
        component={HistoryContainer}
        options={{tabBarLabel: t('tabs.history')}}
      />
      <Tab.Screen name="Plans" component={PlansStack} options={{tabBarLabel: t('tabs.plans')}} />
      {/* tabBarButtonTestID is invisible to jest but used by on-device e2e later. */}
      <Tab.Screen
        name="More"
        component={MoreStack}
        options={{tabBarButtonTestID: 'tab-more', tabBarLabel: t('tabs.more')}}
      />
    </Tab.Navigator>
  );
}
