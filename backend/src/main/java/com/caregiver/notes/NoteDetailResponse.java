package com.caregiver.notes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
    List<NoteAddendumResponse> addenda) {

  static NoteDetailResponse from(DailyNote note, List<NoteAddendumResponse> addenda) {
    return new NoteDetailResponse(
        note.getId(),
        note.getRecipient().getId(),
        note.getDate(),
        note.getMood(),
        note.getAppetite(),
        note.getSleep(),
        note.getMobility(),
        note.getMedicationStatus(),
        note.getMedicationVerified(),
        note.getPainScore(),
        note.getFallReported(),
        note.getFreeText(),
        addenda.size(),
        note.getCreatedAt(),
        List.copyOf(addenda));
  }
}
