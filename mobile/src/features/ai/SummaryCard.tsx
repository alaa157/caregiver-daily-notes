import React from 'react';
import {Text, View} from 'react-native';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import SafetyBanner from '../../components/SafetyBanner';
import {ApiError} from '../../lib/errors';
import {Summary} from '../../types/api';

export interface SummaryCardProps {
  summary: Summary;
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
}

export default function SummaryCard({
  summary,
  state = 'content',
  error = null,
  onRetry,
}: SummaryCardProps): React.JSX.Element {
  const content = (
    <View testID="summary-card">
      <SafetyBanner flags={summary.redFlags} />
      <AppText>{summary.text}</AppText>
      {summary.evidence.map((e, i) => (
        <View key={`${e.noteId}-${i}`} testID={`evidence-quote-${i}`}>
          <Text>{e.quote}</Text>
        </View>
      ))}
      {summary.uncertainties.map((u, i) => (
        <View key={`${u.topic}-${i}`} testID={`uncertainty-${i}`}>
          <Text>{u.detail}</Text>
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
