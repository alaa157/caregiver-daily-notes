package com.caregiver.notes;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.NoteObservations;

import java.time.LocalDate;
import java.util.UUID;

/** API representation of the shared deterministic signal contract. */
public record NoteSignalResponse(
    UUID recipientId,
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

  public NoteSignalResponse(
      UUID recipientId,
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

  public NoteSignals toNoteSignals() {
    return new NoteSignals(
        recipientId.toString(),
        date,
        mood,
        appetite,
        sleep,
        mobility,
        medicationStatus,
        fallReported,
        painScore,
        missedMedication,
        medicationUnverified,
        poorAppetite,
        freeText,
        observations);
  }
}
