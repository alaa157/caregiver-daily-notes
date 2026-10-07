import React from 'react';
import {Text, View} from 'react-native';
import AppText from '../../components/AppText';
import SafetyBanner from '../../components/SafetyBanner';
import {Summary} from '../../types/api';

export default function SummaryCard({summary}: {summary: Summary}): React.JSX.Element {
  return (
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
}
