package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenDatasetTest {

  @Test
  void loadsFirstTenCasesSorted() {
    var cases = GoldenCases.load();
    assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
    assertThat(cases).extracting(GoldenEvalCase::id).isSorted();
    assertThat(cases).allMatch(c -> c.synthetic().equals("synthetic-v1"));
  }
}
