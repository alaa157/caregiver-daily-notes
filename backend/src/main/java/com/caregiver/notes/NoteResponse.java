package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.ObservationState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NoteResponse(
    UUID id,
    UUID recipientId,
    LocalDate date,
    String mood,
    String appetite,
    String sleep,
    String mobility,
    String medicationStatus,
    Boolean medicationVerified,
    Integer painScore,
    Boolean fallReported,
    String freeText,
    long addendumCount,
    Instant createdAt,
    NoteObservations observations,
    long correctionCount) {

  static NoteResponse from(DailyNote note, long addendumCount) {
    return from(note, addendumCount, 0);
  }
  static NoteResponse from(DailyNote note, long addendumCount, long correctionCount) {
    NoteObservations observations = note.observations();
    return new NoteResponse(
        note.getId(),
        note.getRecipient().getId(),
        note.getDate(),
        observations.mood().compatibilityValue(),
        observations.appetite().compatibilityValue(),
        observations.sleep().compatibilityValue(),
        observations.mobility().compatibilityValue(),
        observations.medicationStatus().state() == ObservationState.UNKNOWN
            ? "UNKNOWN" : observations.medicationStatus().compatibilityValue(),
        note.getMedicationVerified(),
        observations.painScore().compatibilityValue(),
        observations.fallReported().compatibilityValue(),
        observations.freeText().compatibilityValue(),
        addendumCount,
        note.getCreatedAt(),
        observations,
        correctionCount);
  }
}
