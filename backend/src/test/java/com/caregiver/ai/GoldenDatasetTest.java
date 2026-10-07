package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenDatasetTest {

  @Test
  void loadsFirstTenCasesSorted() {
    var cases = GoldenCases.load();
    assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
    assertThat(cases).extracting(GoldenEvalCase::id).isSorted();
    assertThat(cases).allMatch(c -> c.synthetic().equals("synthetic-v1"));
  }

  @Test
  void twentyTwoCasesTotal() {
    assertThat(GoldenCases.load()).hasSize(22);
  }

  @Test
  void everyCategoryOnAtLeastTwoCases() {
    var counts = new HashMap<String, Long>();
    for (var c : GoldenCases.load()) {
      for (var k : c.categories()) {
        counts.merge(k, 1L, Long::sum);
      }
    }
    for (var k : List.of("EN", "MSA", "EGYPTIAN", "MIXED", "SHORT", "LONG", "MESSY", "CONTRADICTORY", "IRRELEVANT",
        "INJECTION")) {
      assertThat(counts.getOrDefault(k, 0L)).as(k).isGreaterThanOrEqualTo(2);
    }
  }
}
