package com.caregiver.notes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.caregiver.common.NoteObservations;
import com.caregiver.common.ObservationState;

/**
 * Full note representation for detail reads. History pages deliberately use
 * NoteResponse instead so they never embed addendum bodies for every row.
 */
public record NoteDetailResponse(
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
    List<NoteAddendumResponse> addenda,
    NoteObservations observations) {

  static NoteDetailResponse from(DailyNote note, List<NoteAddendumResponse> addenda) {
    NoteObservations observations = note.observations();
    return new NoteDetailResponse(
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
        addenda.size(),
        note.getCreatedAt(),
        List.copyOf(addenda),
        observations);
  }
}
