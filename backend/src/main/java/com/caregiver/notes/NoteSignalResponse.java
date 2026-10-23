package com.caregiver.notes;

import com.caregiver.common.NoteSignals;

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
    Boolean fallReported,
    Integer painScore,
    Boolean missedMedication,
    boolean medicationUnverified,
    Boolean poorAppetite,
    String freeText) {

  public NoteSignals toNoteSignals() {
    return new NoteSignals(
        recipientId.toString(),
        date,
        mood,
        appetite,
        sleep,
        mobility,
        null,
        fallReported,
        painScore,
        missedMedication,
        medicationUnverified,
        poorAppetite,
        freeText);
  }
}
