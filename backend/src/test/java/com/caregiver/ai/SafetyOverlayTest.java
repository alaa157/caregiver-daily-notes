package com.caregiver.ai;

import com.caregiver.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyOverlayTest {

  private final SafetyOverlay overlay = new SafetyOverlay();

  @Test
  void llmCannotDowngradeHighToWatch() {
    var det = new SafetyEvaluation(
        List.of(new SafetySignal(SafetyRuleId.FALL_DETECTED, Severity.HIGH, "fall")), true);
    var merged = overlay.merge(
        det, new AiSuggestion(Map.of(SafetyRuleId.FALL_DETECTED, Severity.WATCH), null));
    var signal = merged.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.FALL_DETECTED).findFirst().orElseThrow();
    assertThat(signal.severity()).isEqualTo(Severity.HIGH);
    assertThat(merged.needsDoctorBanner()).isTrue();
  }

  @Test
  void llmCannotClearDoctorBanner() {
    var det = new SafetyEvaluation(
        List.of(new SafetySignal(SafetyRuleId.HIGH_PAIN, Severity.HIGH, "pain 9")), true);
    var merged = overlay.merge(det, new AiSuggestion(Map.of(), false));
    assertThat(merged.needsDoctorBanner()).isTrue();
    assertThat(merged.signals()).extracting(SafetySignal::severity).containsExactly(Severity.HIGH);
  }

  @Test
  void llmCanEscalateWatchToHigh_setsBanner() {
    var det = new SafetyEvaluation(
        List.of(new SafetySignal(SafetyRuleId.POOR_APPETITE_DAYS, Severity.WATCH, "3 days")), false);
    var merged = overlay.merge(
        det, new AiSuggestion(Map.of(SafetyRuleId.POOR_APPETITE_DAYS, Severity.HIGH), null));
    var signal = merged.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.POOR_APPETITE_DAYS).findFirst().orElseThrow();
    assertThat(signal.severity()).isEqualTo(Severity.HIGH);
    assertThat(merged.needsDoctorBanner()).isTrue();
  }

  @Test
  void llmHallucinatedRule_isIgnored() {
    var det = new SafetyEvaluation(List.of(), false);
    var merged = overlay.merge(
        det, new AiSuggestion(Map.of(SafetyRuleId.HIGH_PAIN, Severity.HIGH), null));
    assertThat(merged.signals()).isEmpty();
    assertThat(merged.needsDoctorBanner()).isFalse();
  }

  @Test
  void nullSuggestion_returnsDeterministic() {
    var det = new SafetyEvaluation(
        List.of(new SafetySignal(SafetyRuleId.FALL_DETECTED, Severity.HIGH, "fall")), true);
    assertThat(overlay.merge(det, null)).isSameAs(det);
    assertThat(overlay.merge(det, new AiSuggestion(null, null))).isSameAs(det);
  }

  @Test
  void nullDeterministic_throws() {
    assertThatThrownBy(() -> overlay.merge(null, new AiSuggestion(Map.of(), true)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
