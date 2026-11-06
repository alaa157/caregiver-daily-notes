package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceValidatorTest {

  static GroundedSummary summary(SummaryObservation... observations) {
    return new GroundedSummary(List.of(observations), List.of(), false);
  }

  @Test
  void unknownNoteId_rejectedWithIndex() {
    var bad = summary(new SummaryObservation("ate well", "n9", "ate well"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "ate well")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("0");
  }

  @Test
  void emptyQuote_rejected() {
    var bad = summary(new SummaryObservation("ate well", "n1", "  "));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "ate well")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void rejectsInventedQuote() {
    var bad = summary(new SummaryObservation("ate well", "n1", "slept perfectly"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "she ate well today")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void rejectsQuoteFoundOnlyInAnotherNote() {
    var bad = summary(new SummaryObservation("ate well", "n1", "slept well"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "ate well", "n2", "slept well")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void validArabicQuoteAfterExistingNormalizationPreservesOriginalNoteId() {
    var good = summary(new SummaryObservation("reported eating", "source-note-1", "يَأْكُلُ جَيِّدًا"));

    var validated = EvidenceValidator.validate(good, Map.of("source-note-1", "المريض ياكل جيدا اليوم"));

    assertThat(validated.observations().getFirst().noteId()).isEqualTo("source-note-1");
    assertThat(validated.observations().getFirst().quote()).isEqualTo("يَأْكُلُ جَيِّدًا");
    assertThat(validated).isSameAs(good);
  }

  @Test
  void validEvidence_passesThroughSameInstance() {
    var good = summary(new SummaryObservation("ate well", "n1", "ate well"));

    assertThat(EvidenceValidator.validate(good, Map.of("n1", "she ate well today"))).isSameAs(good);
  }

  @Test
  void nullSummaryOrMap_rejected() {
    assertThatThrownBy(() -> EvidenceValidator.validate(null, Map.of("n1", "x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EvidenceValidator.validate(summary(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
