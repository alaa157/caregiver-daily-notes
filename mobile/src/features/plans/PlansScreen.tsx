import React from 'react';
import {View} from 'react-native';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppButton from '../../components/AppButton';
import AppText from '../../components/AppText';
import {ApiError} from '../../lib/errors';
import {Plan, PlanAction} from '../../types/api';
import {t} from '../../i18n/i18n';

export interface PlansScreenProps {
  plans: Plan[];
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
}

export function PlansScreen({
  plans,
  state = 'content',
  error = null,
  onRetry,
}: PlansScreenProps): React.JSX.Element {
  const content = (
    <View testID="plans-screen">
      {plans.map(p => (
        <View key={p.id} testID="plan-row">
          <AppText>{p.id}</AppText>
        </View>
      ))}
    </View>
  );
  if (state === 'error') {
    return (
      <ApiStateView state="error" error={error} onRetry={onRetry}>
        {content}
      </ApiStateView>
    );
  }
  return (
    <ApiStateView state={state} onRetry={onRetry}>
      {content}
    </ApiStateView>
  );
}

const ACTIONS: Array<{testID: string; action: PlanAction; label: string}> = [
  {testID: 'plan-action-accept', action: 'accept', label: 'Accept'},
  {testID: 'plan-action-edit-accept', action: 'edit-accept', label: 'Edit & accept'},
  {testID: 'plan-action-dismiss', action: 'dismiss', label: 'Dismiss'},
  {testID: 'plan-action-archive', action: 'archive', label: 'Archive'},
];

export interface PlanDetailProps {
  plan: Plan;
  onAction: (action: PlanAction) => void;
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
}

export default function PlanDetailScreen({
  plan,
  onAction,
  state = 'content',
  error = null,
  onRetry,
}: PlanDetailProps): React.JSX.Element {
  const content = (
    <View testID="plan-detail">
      {ACTIONS.map(a => (
        <AppButton
          key={a.testID}
          testID={a.testID}
          title={a.label}
          onPress={() => onAction(a.action)}
        />
      ))}
      {plan.versions.map((v, i) => (
        <View key={v.version} testID={`plan-version-${i}`}>
          <AppText>{`${v.version} ${v.status}`}</AppText>
        </View>
      ))}
      <AppText>{t('plans.title')}</AppText>
    </View>
  );
  if (state === 'error') {
    return (
      <ApiStateView state="error" error={error} onRetry={onRetry}>
        {content}
      </ApiStateView>
    );
  }
  return (
    <ApiStateView state={state} onRetry={onRetry}>
      {content}
    </ApiStateView>
  );
}
