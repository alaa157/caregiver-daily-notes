package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteValidationTest {

  static GroundedSummary summary(SummaryObservation... observations) {
    return new GroundedSummary(List.of(observations), List.of(), false);
  }

  @Test
  void egyptianQuote_matchesVoweledNote() {
    var good = summary(new SummaryObservation("eating well", "n1", "ياكل جيدا"));

    assertThat(EvidenceValidator.validate(good, Map.of("n1", "يَأْكُلُ جَيِّدًا"))).isSameAs(good);
  }

  @Test
  void msaQuote_matchesNote() {
    var good = summary(new SummaryObservation("walked", "n1", "مشى مع ابنته"));

    assertThat(EvidenceValidator.validate(good, Map.of("n1", "خرج مشى مع ابنته اليوم"))).isSameAs(good);
  }

  @Test
  void mixedLanguageQuote_matchesNote() {
    var good = summary(new SummaryObservation("mood", "n1", "mood كان CALM"));

    assertThat(EvidenceValidator.validate(good, Map.of("n1", "The mood كان calm الحمدلله"))).isSameAs(good);
  }

  @Test
  void nonSubstringQuote_rejected() {
    var bad = summary(new SummaryObservation("slept", "n1", "نام عشر ساعات"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "أكل جيدا")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void quoteFromWrongNote_rejected() {
    var bad = summary(new SummaryObservation("ate", "n1", "أكل جيدا"));

    assertThatThrownBy(() -> EvidenceValidator.validate(bad, Map.of("n1", "مشى اليوم", "n2", "أكل جيدا")))
        .isInstanceOf(InvalidModelOutputException.class);
  }
}
