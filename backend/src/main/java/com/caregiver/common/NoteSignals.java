package com.caregiver.common;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable contract for one day's structured caregiver observations.
 * Nullable fields mean the observation was not supplied or remains unknown.
 * Raw observation strings are preserved verbatim; deterministic safety rules
 * consume only the explicit safety projection fields.
 */
public record NoteSignals(
    String recipientId,
    LocalDate date,
    String mood,
    String appetite,
    String sleep,
    String mobility,
    String medicationStatus,
    Boolean fallReported,
    Integer painScore,
    Boolean missedMedication,
    boolean medicationUnverified,
    Boolean poorAppetite,
    String freeText) {

  /**
   * Backwards-compatible constructor for safety-only producers and fixtures.
   */
  public NoteSignals(
      String recipientId,
      LocalDate date,
      Boolean fallReported,
      Integer painScore,
      Boolean missedMedication,
      boolean medicationUnverified,
      Boolean poorAppetite,
      String freeText) {
    this(recipientId, date, null, null, null, null, null, fallReported, painScore,
        missedMedication, medicationUnverified, poorAppetite, freeText);
  }

  public NoteSignals {
    Objects.requireNonNull(date, "date");
    if (painScore != null && (painScore < 0 || painScore > 10)) {
      throw new IllegalArgumentException("painScore 0-10 or null");
    }
  }
}
