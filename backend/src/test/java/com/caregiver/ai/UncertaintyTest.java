package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the explicit-uncertainty representation: contradictions and unclear
 * information travel as uncertainties, never as guessed observations.
 */
class UncertaintyTest {

  @Test
  void contradictoryNotes_becomeUncertaintyNotObservation() {
    var summary = new GroundedSummary(
        List.of(),
        List.of(new SummaryUncertainty("medication", "taken and missed reported same day")),
        false);

    assertThat(EvidenceValidator.validate(summary, Map.of("n1", "took meds", "n2", "missed meds")))
        .isSameAs(summary);
    assertThat(summary.observations()).isEmpty();
    assertThat(summary.uncertainties()).hasSize(1);
  }

  @Test
  void uncertainties_surviveValidation() {
    var summary = new GroundedSummary(
        List.of(new SummaryObservation("ate well", "n1", "ate well")),
        List.of(new SummaryUncertainty("sleep", "no sleep information reported")),
        false);

    var validated = EvidenceValidator.validate(summary, Map.of("n1", "she ate well today"));

    assertThat(validated.uncertainties())
        .containsExactly(new SummaryUncertainty("sleep", "no sleep information reported"));
  }

  @Test
  void emptyObservationsWithUncertainty_isValid() {
    var summary = new GroundedSummary(
        List.of(),
        List.of(new SummaryUncertainty("appetite", "unclear_or_conflicting entries")),
        false);

    assertThat(EvidenceValidator.validate(summary, Map.of()).observations()).isEmpty();
  }
}
