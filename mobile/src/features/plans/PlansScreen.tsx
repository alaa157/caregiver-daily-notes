import React from 'react';
import {View} from 'react-native';
import AppButton from '../../components/AppButton';
import AppText from '../../components/AppText';
import {Plan, PlanAction} from '../../types/api';
import {t} from '../../i18n/i18n';

export function PlansScreen({plans}: {plans: Plan[]}): React.JSX.Element {
  return (
    <View testID="plans-screen">
      {plans.map(p => (
        <View key={p.id} testID="plan-row">
          <AppText>{p.id}</AppText>
        </View>
      ))}
    </View>
  );
}

const ACTIONS: Array<{testID: string; action: PlanAction; label: string}> = [
  {testID: 'plan-action-accept', action: 'accept', label: 'Accept'},
  {testID: 'plan-action-edit-accept', action: 'edit-accept', label: 'Edit & accept'},
  {testID: 'plan-action-dismiss', action: 'dismiss', label: 'Dismiss'},
  {testID: 'plan-action-archive', action: 'archive', label: 'Archive'},
];

export default function PlanDetailScreen({
  plan,
  onAction,
}: {
  plan: Plan;
  onAction: (action: PlanAction) => void;
}): React.JSX.Element {
  return (
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
}
