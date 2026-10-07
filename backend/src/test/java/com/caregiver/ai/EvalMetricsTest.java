package com.caregiver.ai;

import com.caregiver.ai.GoldenEvalCase.QuoteRef;
import com.caregiver.ai.GoldenEvalCase.TestSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalMetricsTest {

  static GoldenEvalCase.TestSignal signal(boolean fall) {
    return new TestSignal(fall, null, null, false, null, fall ? "she fell today" : "routine day", 0);
  }

  static GoldenEvalCase golden(String id, boolean fall, List<String> expectedFlags) {
    return new GoldenEvalCase(
        id, "synthetic-v1", List.of("EN"), List.of(signal(fall)),
        "{\"observations\":[],\"uncertainties\":[]}", "OK",
        List.of(new QuoteRef(0, fall ? "she fell" : "routine")),
        expectedFlags, true, true, true);
  }

  @Test
  void perfectCase_scoresAllTrue() {
    var result = new AiEvaluator().evaluateOne(golden("c1", true, List.of("FALL_DETECTED")));

    assertThat(result.flagsHit()).isTrue();
    assertThat(result.schemaValid()).isTrue();
    assertThat(result.grounded()).isTrue();
    assertThat(result.medsSafe()).isTrue();
    assertThat(result.failures()).isEmpty();
  }

  @Test
  void missedFlag_scoresRecallFalse() {
    var result = new AiEvaluator().evaluateOne(golden("c2", false, List.of("FALL_DETECTED")));

    assertThat(result.flagsHit()).isFalse();
    assertThat(result.schemaValid()).isTrue();
    assertThat(result.failures()).containsExactly("c2:flags");
  }

  @Test
  void emptyExpectations_excludedFromRecall() {
    var metrics = new AiEvaluator().evaluate(List.of(golden("c3", false, List.of())));

    assertThat(metrics.totalCases()).isEqualTo(1);
    assertThat(metrics.redFlagRecall()).isEqualTo(1.0);
    assertThat(metrics.failures()).isEmpty();
  }

  @Test
  void aggregate_countsRatesAndListsFailures() {
    var metrics = new AiEvaluator().evaluate(List.of(
        golden("c4", true, List.of("FALL_DETECTED")),
        golden("c5", false, List.of("FALL_DETECTED"))));

    assertThat(metrics.totalCases()).isEqualTo(2);
    assertThat(metrics.redFlagRecall()).isEqualTo(0.5);
    assertThat(metrics.schemaValidRate()).isEqualTo(1.0);
    assertThat(metrics.failures()).containsExactly("c5:flags");
  }
}
