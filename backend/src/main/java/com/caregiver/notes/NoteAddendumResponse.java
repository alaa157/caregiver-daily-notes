package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.ObservationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteAddendumResponse(
    UUID id,
    UUID noteId,
    String mood,
    String appetite,
    String sleep,
    String mobility,
    String medicationStatus,
    Boolean medicationVerified,
    Integer painScore,
    Boolean fallReported,
    String freeText,
    Instant createdAt,
    NoteObservations observations,
    List<String> providedFields) {

  static NoteAddendumResponse from(NoteAddendum addendum) {
    NoteObservations observations = addendum.observations();
    return new NoteAddendumResponse(
        addendum.getId(),
        addendum.getNote().getId(),
        observations.mood().compatibilityValue(),
        observations.appetite().compatibilityValue(),
        observations.sleep().compatibilityValue(),
        observations.mobility().compatibilityValue(),
        observations.medicationStatus().state() == ObservationState.UNKNOWN
            ? "UNKNOWN" : observations.medicationStatus().compatibilityValue(),
        addendum.getMedicationVerified(),
        observations.painScore().compatibilityValue(),
        observations.fallReported().compatibilityValue(),
        observations.freeText().compatibilityValue(),
        addendum.getCreatedAt(),
        observations,
        addendum.observationPresence().fieldNames());
  }
}