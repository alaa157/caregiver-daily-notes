package com.caregiver.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportTest {

  @Test
  void report_containsEveryRequiredMetric() throws Exception {
    var metrics = new AiEvaluator().evaluate(GoldenCases.load());
    var path = Path.of(EvalReport.DEFAULT_PATH);
    EvalReport.write(metrics, path);

    var tree = new ObjectMapper().readTree(path.toFile());
    assertThat(tree.hasNonNull("redFlagRecall")).isTrue();
    assertThat(tree.hasNonNull("schemaValidRate")).isTrue();
    assertThat(tree.hasNonNull("groundednessRate")).isTrue();
    assertThat(tree.hasNonNull("medicationSafetyRate")).isTrue();
    assertThat(tree.hasNonNull("totalCases")).isTrue();
    assertThat(tree.hasNonNull("failures")).isTrue();
    assertThat(tree.get("totalCases").asInt()).isEqualTo(22);
  }

  @Test
  void allMetricsPerfect() {
    var metrics = new AiEvaluator().evaluate(GoldenCases.load());

    assertThat(metrics.redFlagRecall()).isEqualTo(1.0);
    assertThat(metrics.schemaValidRate()).isEqualTo(1.0);
    assertThat(metrics.groundednessRate()).isEqualTo(1.0);
    assertThat(metrics.medicationSafetyRate()).isEqualTo(1.0);
    assertThat(metrics.failures()).isEmpty();
  }
}
