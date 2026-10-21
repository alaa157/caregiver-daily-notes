package com.caregiver.common;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable input struct for one day of caregiver note signals.
 * Stub for the notes schema owned by Ebrahim Ahmed (issues #30 and #34).
 * {@code freeText} is ignored by the evaluator.
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
