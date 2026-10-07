import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import HistoryHomeScreen from '../features/history/HistoryHomeScreen';
import NotesHomeScreen from '../features/notes/NotesHomeScreen';
import PlansHomeScreen from '../features/plans/PlansHomeScreen';
import MoreStack from './MoreStack';

const Tab = createBottomTabNavigator();

export default function MainTabs(): React.JSX.Element {
  return (
    <Tab.Navigator>
      <Tab.Screen name="Notes" component={NotesHomeScreen} />
      <Tab.Screen name="History" component={HistoryHomeScreen} />
      <Tab.Screen name="Plans" component={PlansHomeScreen} />
      {/* tabBarButtonTestID is invisible to jest but used by on-device e2e later. */}
      <Tab.Screen name="More" component={MoreStack} options={{tabBarButtonTestID: 'tab-more'}} />
    </Tab.Navigator>
  );
}
