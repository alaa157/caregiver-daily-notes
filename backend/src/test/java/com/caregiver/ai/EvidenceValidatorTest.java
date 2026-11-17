package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

  @Test
  void singleFieldLabelQuote_rejected() {
    var bad = summary(new SummaryObservation("ate well", "n1", "mood"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad,
        Map.of("n1", "mood [RECORDED]: she ate well today")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("field label");
  }

  @Test
  void stateMarkerOnlyQuote_rejected() {
    var bad = summary(new SummaryObservation("ate well", "n1", "RECORDED"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad,
        Map.of("n1", "mood [RECORDED]: she ate well today")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("field label");
  }

  @Test
  void labelAndStateMarkerQuote_rejected() {
    var bad = summary(new SummaryObservation("ate well", "n1", "mood [RECORDED]"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad,
        Map.of("n1", "mood [RECORDED]: she ate well today")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("field label");
  }

  @Test
  void quoteShorterThanNamedMinimum_rejected() {
    String shortQuote = "a".repeat(EvidenceValidator.MIN_QUOTE_LENGTH - 1);
    var bad = summary(new SummaryObservation("ate well", "n1", shortQuote));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", shortQuote)))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("too short");
  }

  @Test
  void singleWordQuote_rejectedEvenWhenLongEnough() {
    String word = "a".repeat(EvidenceValidator.MIN_QUOTE_LENGTH + 1);
    var bad = summary(new SummaryObservation("ate well", "n1", word));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", word)))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("too short");
  }

  @Test
  void realSentenceQuoteFromScaffoldedNote_accepted() {
    var good = summary(new SummaryObservation("she ate well", "n1", "she ate well today"));

    assertThat(EvidenceValidator.validate(good,
        Map.of("n1", "mood [RECORDED]: she ate well today"))).isSameAs(good);
  }

  @Test
  void patternClaimCitingTwoNotes_accepted() {
    var good = summary(new SummaryObservation("ate then slept well", "n1", "she ate well",
        List.of("n1", "n2"), List.of("she ate well", "slept ten hours")));

    assertThat(EvidenceValidator.validate(good,
        Map.of("n1", "she ate well today", "n2", "slept ten hours"))).isSameAs(good);
  }

  @Test
  void patternClaimCitingOneNote_rejected() {
    var bad = summary(new SummaryObservation("ate then slept well", "n1", "she ate well",
        List.of("n1"), List.of("she ate well")));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "she ate well today")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("distinct notes");
  }

  @Test
  void patternClaimWithMismatchedCitations_rejected() {
    var bad = summary(new SummaryObservation("ate then slept well", "n1", "she ate well",
        List.of("n1", "n2"), List.of("she ate well")));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad,
        Map.of("n1", "she ate well today", "n2", "slept ten hours")))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("one quote each");
  }

  @Test
  void strictModelOutputWithPatternCitations_parsesAndValidates() {
    String json = "{\"observations\":[{\"text\":\"ate then slept well\",\"noteId\":\"n1\","
        + "\"quote\":\"she ate well\",\"noteIds\":[\"n1\",\"n2\"],"
        + "\"quotes\":[\"she ate well\",\"slept ten hours\"]}],\"uncertainties\":[]}";
    var parsed = StrictJsonParser.parse(json, SummaryService.ModelSummary.class,
        Set.of("observations", "uncertainties"));
    var grounded = new GroundedSummary(parsed.observations(), parsed.uncertainties(), false);

    assertThat(EvidenceValidator.validate(grounded,
        Map.of("n1", "she ate well today", "n2", "slept ten hours"))).isSameAs(grounded);
    assertThat(grounded.observations().getFirst().noteIds()).containsExactly("n1", "n2");
  }
}
