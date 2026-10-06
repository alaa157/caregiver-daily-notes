package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputParserTest {

  enum Mood {
    CALM,
    LOW
  }

  record Probe(String a, Mood mood) {
  }

  @Test
  void strip_removesThinkTagsAndFences() {
    var raw = "<think>reasoning here</think>\n```json\n{\"a\":\"x\"}\n```";
    assertThat(ModelSanitizer.strip(raw)).isEqualTo("{\"a\":\"x\"}");
  }

  @Test
  void strip_nullYieldsEmpty() {
    assertThat(ModelSanitizer.strip(null)).isEmpty();
  }

  @Test
  void parse_rejectsUnknownProperties() {
    assertThatThrownBy(() -> StrictJsonParser.parse("{\"a\":\"x\",\"zzz\":1}", Probe.class, Set.of("a")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void parse_rejectsInvalidEnum() {
    assertThatThrownBy(
        () -> StrictJsonParser.parse("{\"a\":\"x\",\"mood\":\"EUPHORIC\"}", Probe.class, Set.of("a", "mood")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void parse_rejectsMissingRequiredField() {
    assertThatThrownBy(() -> StrictJsonParser.parse("{\"mood\":\"CALM\"}", Probe.class, Set.of("a", "mood")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void parse_acceptsCleanJson() {
    var probe = StrictJsonParser.parse("{\"a\":\"x\",\"mood\":\"CALM\"}", Probe.class, Set.of("a", "mood"));
    assertThat(probe.a()).isEqualTo("x");
    assertThat(probe.mood()).isEqualTo(Mood.CALM);
  }

  @Test
  void fencedValidJson_parsesAfterStrip() {
    var probe = StrictJsonParser.parse("```json\n{\"a\":\"v\"}\n```", Probe.class, Set.of("a"));
    assertThat(probe.a()).isEqualTo("v");
  }
}
