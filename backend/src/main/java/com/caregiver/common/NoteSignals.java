package com.caregiver.common;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable contract for one day's structured caregiver observations.
 * The typed observations carry the observation/verification semantics; legacy
 * nullable fields remain a compatibility and safety projection only.
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
    String freeText,
    NoteObservations observations) {

  public NoteSignals(
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
    this(recipientId, date, mood, appetite, sleep, mobility, medicationStatus, fallReported,
        painScore, missedMedication, medicationUnverified, poorAppetite, freeText,
        NoteObservations.notRecorded());
  }

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
        missedMedication, medicationUnverified, poorAppetite, freeText, NoteObservations.notRecorded());
  }

  public NoteSignals {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(observations, "observations");
    if (painScore != null && (painScore < 0 || painScore > 10)) {
      throw new IllegalArgumentException("painScore 0-10 or null");
    }
  }
}
