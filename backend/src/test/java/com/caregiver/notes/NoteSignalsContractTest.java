package com.caregiver.notes;

import com.caregiver.common.NoteSignals;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class NoteSignalsContractTest {
  @Test
  void preservesMixedLanguageTextAndAbsentPain() {
    String s = "المريض قال: I feel pain — لا تغيّر النص.";
    var n = new NoteSignals("r1", LocalDate.of(2026, 10, 1), false, null, null, true, null, s);
    assertThat(n.painScore()).isNull();
    assertThat(n.freeText()).isEqualTo(s);
    assertThat(n.medicationUnverified()).isTrue();
  }

  @Test
  void missingFallObservationRemainsNull() {
    var n = new NoteSignals("r1", LocalDate.of(2026, 10, 1), null, null, null, false, null, null);
    assertThat(n.fallReported()).isNull();
    assertThat(n.painScore()).isNull();
    assertThat(n.missedMedication()).isNull();
    assertThat(n.poorAppetite()).isNull();
  }


  @Test
  void noteSignalResponseMapsAllReportedObservationsToSharedContract() {
    var date = LocalDate.of(2026, 10, 23);
    var response = new NoteSignalResponse(
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
        date, "calm", "poor", "interrupted", "walking with help", "UNKNOWN",
        null, null, null, true, null, "raw text");
    var signals = response.toNoteSignals();

    assertThat(signals.mood()).isEqualTo("calm");
    assertThat(signals.appetite()).isEqualTo("poor");
    assertThat(signals.sleep()).isEqualTo("interrupted");
    assertThat(signals.mobility()).isEqualTo("walking with help");
    assertThat(signals.medicationStatus()).isEqualTo("UNKNOWN");
    assertThat(signals.fallReported()).isNull();
    assertThat(signals.painScore()).isNull();
    assertThat(signals.freeText()).isEqualTo("raw text");
  }

  @Test
  void rejectsPainOutsideRange() {
    assertThatThrownBy(() -> new NoteSignals("r1", LocalDate.now(), false, 11, null, false, null, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}