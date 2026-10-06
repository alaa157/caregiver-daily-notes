package com.caregiver.ai;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Service input for one stored note. Carries the caller-supplied note id so
 * evidence can cite it, keeping the service decoupled from Member 2 storage.
 */
public record SummaryNote(String noteId, LocalDate date, String text) {

  public SummaryNote {
    if (noteId == null || noteId.isBlank()) {
      throw new IllegalArgumentException("noteId must be non-blank");
    }
    Objects.requireNonNull(date, "date");
    if (text == null) {
      throw new IllegalArgumentException("text must be non-null (may be empty)");
    }
  }
}
