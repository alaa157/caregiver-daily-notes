import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import HistoryHomeScreen from '../features/history/HistoryHomeScreen';
import NotesHomeScreen from '../features/notes/NotesHomeScreen';
import PlansHomeScreen from '../features/plans/PlansHomeScreen';
import MoreHomeScreen from './MoreHomeScreen';

const Tab = createBottomTabNavigator();

export default function MainTabs(): React.JSX.Element {
  return (
    <Tab.Navigator>
      <Tab.Screen name="Notes" component={NotesHomeScreen} />
      <Tab.Screen name="History" component={HistoryHomeScreen} />
      <Tab.Screen name="Plans" component={PlansHomeScreen} />
      <Tab.Screen name="More" component={MoreHomeScreen} />
    </Tab.Navigator>
  );
}
