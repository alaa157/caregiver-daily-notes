import {createNativeStackNavigator, NativeStackScreenProps} from '@react-navigation/native-stack';
import React, {useCallback, useEffect, useState} from 'react';
import {Button, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import {ApiError} from '../../lib/errors';
import {useAuth} from '../auth/AuthContext';
import {Plan, PlanAction} from '../../types/api';
import {listPlans, transitionPlan} from './api';
import PlanDetailScreen, {PlansScreen} from './PlansScreen';

export type PlansStackParamList = {
  PlansList: undefined;
  PlanDetail: {planId: string};
};

const Stack = createNativeStackNavigator<PlansStackParamList>();

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Unexpected error');
}

type ListRouteProps = NativeStackScreenProps<PlansStackParamList, 'PlansList'>;
type DetailRouteProps = NativeStackScreenProps<PlansStackParamList, 'PlanDetail'>;

function PlansListRoute({navigation}: ListRouteProps): React.JSX.Element {
  const {authedFetch} = useAuth();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const rows = await authedFetch(client => listPlans(client));
      setPlans(rows);
      setState(rows.length === 0 ? 'empty' : 'content');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <PlansScreen
      plans={plans}
      state={state}
      error={error}
      onRetry={() => void load()}
      onSelect={p => navigation.navigate('PlanDetail', {planId: p.id})}
    />
  );
}

function PlanDetailRoute({navigation, route}: DetailRouteProps): React.JSX.Element {
  const {authedFetch} = useAuth();
  const {t} = useTranslation();
  const [plan, setPlan] = useState<Plan | null>(null);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const rows = await authedFetch(client => listPlans(client));
      const found = rows.find(p => p.id === route.params.planId) ?? null;
      setPlan(found);
      setState(found ? 'content' : 'empty');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch, route.params.planId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleAction(action: PlanAction): Promise<void> {
    try {
      await authedFetch(client => transitionPlan(client, route.params.planId, action));
      await load();
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }

  if (!plan) {
    if (state === 'error') {
      return <ApiStateView state="error" error={error} onRetry={() => void load()} />;
    }
    return <ApiStateView state={state} onRetry={() => void load()} />;
  }
  return (
    <View>
      <PlanDetailScreen
        plan={plan}
        onAction={action => void handleAction(action)}
        state={state}
        error={error}
        onRetry={() => void load()}
      />
      <Button testID="plan-detail-back" title={t('common.back')} onPress={() => navigation.goBack()} />
    </View>
  );
}

export default function PlansStack(): React.JSX.Element {
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      <Stack.Screen name="PlansList" component={PlansListRoute} />
      <Stack.Screen name="PlanDetail" component={PlanDetailRoute} />
    </Stack.Navigator>
  );
}
