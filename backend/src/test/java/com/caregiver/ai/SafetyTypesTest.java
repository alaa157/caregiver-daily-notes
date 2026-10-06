package com.caregiver.ai;

import com.caregiver.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyTypesTest {

  @Test
  void ruleIds_areStableWireValues() {
    assertThat(SafetyRuleId.FALL_DETECTED.getId()).isEqualTo("FALL_DETECTED");
    assertThat(SafetyRuleId.HIGH_PAIN.getId()).isEqualTo("HIGH_PAIN");
    assertThat(SafetyRuleId.REPEATED_MISSED_MED.getId()).isEqualTo("REPEATED_MISSED_MED");
    assertThat(SafetyRuleId.POOR_APPETITE_DAYS.getId()).isEqualTo("POOR_APPETITE_DAYS");
  }

  @Test
  void evaluation_defensivelyCopiesSignals() {
    var list = new ArrayList<SafetySignal>();
    var eval = new SafetyEvaluation(list, false);
    list.add(new SafetySignal(SafetyRuleId.FALL_DETECTED, Severity.HIGH, "x"));
    assertThat(eval.signals()).isEmpty();
  }
}
