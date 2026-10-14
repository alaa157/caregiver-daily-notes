package com.caregiver.common;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable contract for one day's structured caregiver observations.
 * Nullable fields mean the observation was not supplied or remains unknown.
 * Free text is preserved as source material and is not interpreted by safety rules.
 */
public record NoteSignals(
    String recipientId,
    LocalDate date,
    Boolean fallReported,
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
