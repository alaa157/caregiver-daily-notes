package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Summary text is held to the same bilingual content standard as plan text. */
class SummaryTextSafetyTest {

  private static SummaryObservation observation(String text) {
    return new SummaryObservation(text, "n1", "verbatim quote");
  }

  @Test
  void dropsEnglishDiagnosisButKeepsTheRest() {
    var safe = observation("Reported appetite varied today.");
    var unsafe = observation("The caregiver was diagnosed with pneumonia.");

    var screened = SummaryTextSafety.screen(List.of(safe, unsafe), List.of());

    assertThat(screened.observations()).containsExactly(safe);
    assertThat(screened.removedCount()).isEqualTo(1);
  }

  @Test
  void dropsEnglishPrescription() {
    var unsafe = observation("Please increase the dose at night.");

    var screened = SummaryTextSafety.screen(List.of(unsafe), List.of());

    assertThat(screened.observations()).isEmpty();
    assertThat(screened.removedCount()).isEqualTo(1);
  }

  @Test
  void dropsArabicPrescription() {
    var safe = observation("الشهية كانت جيدة اليوم.");
    var unsafe = observation("زيد الجرعة في المساء.");

    var screened = SummaryTextSafety.screen(List.of(safe, unsafe), List.of());

    assertThat(screened.observations()).containsExactly(safe);
    assertThat(screened.removedCount()).isEqualTo(1);
  }

  @Test
  void dropsUnsafeUncertaintyButKeepsCleanOnes() {
    var safe = new SummaryUncertainty("Medication", "The report is unclear.");
    var unsafe = new SummaryUncertainty("Diagnosis", "ابدأ دواء جديد");

    var screened = SummaryTextSafety.screen(List.of(), List.of(safe, unsafe));

    assertThat(screened.uncertainties()).containsExactly(safe);
    assertThat(screened.removedCount()).isEqualTo(1);
  }

  @Test
  void cleanSummaryIsUnaffected() {
    var observations = List.of(observation("Slept well"), observation("المزاج هادئ"));
    var uncertainties = List.of(new SummaryUncertainty("Appetite", "Mixed reports."));

    var screened = SummaryTextSafety.screen(observations, uncertainties);

    assertThat(screened.observations()).containsExactlyElementsOf(observations);
    assertThat(screened.uncertainties()).containsExactlyElementsOf(uncertainties);
    assertThat(screened.removedCount()).isZero();
  }
}
