package com.caregiver.common;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable input struct for one day of caregiver note signals.
 * Stub for Member 2 TASK 1/5 schema. {@code freeText} is ignored by the evaluator.
 */
public record NoteSignals(
    String recipientId,
    LocalDate date,
    boolean fallReported,
    Integer painScore,
    Boolean missedMedication,
    boolean medicationUnverified,
    Boolean poorAppetite,
    String freeText) {

  public NoteSignals {
    Objects.requireNonNull(date, "date");
    if (painScore != null && (painScore < 0 || painScore > 10)) {
      throw new IllegalArgumentException("painScore 0-10 or null");
    }
  }
}
