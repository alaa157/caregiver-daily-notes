package com.caregiver.ai;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared membership checks for AI-generated and caregiver-authored plan evidence. */
public final class PlanEvidenceValidator {
  private PlanEvidenceValidator() {}

  public static UUID parseNoteId(String noteId, int changeIndex) {
    try {
      return UUID.fromString(noteId);
    } catch (IllegalArgumentException invalidId) {
      throw unknownNote(changeIndex, noteId, invalidId);
    }
  }

  public static void validateNoteIds(List<String> noteIds, Set<String> validNoteIds, int changeIndex) {
    for (String noteId : noteIds) {
      if (!validNoteIds.contains(noteId)) {
        throw unknownNote(changeIndex, noteId, null);
      }
    }
  }

  public static void validateRecipientNoteIds(
      List<String> noteIds, Set<UUID> validNoteIds, int changeIndex) {
    for (String noteId : noteIds) {
      if (!validNoteIds.contains(parseNoteId(noteId, changeIndex))) {
        throw unknownNote(changeIndex, noteId, null);
      }
    }
  }

  private static InvalidModelOutputException unknownNote(int changeIndex, String noteId, Throwable cause) {
    String source = changeIndex < 0 ? "Plan" : "Change " + changeIndex;
    String message = source + " cites unknown note: " + noteId;
    return cause == null
        ? new InvalidModelOutputException(message)
        : new InvalidModelOutputException(message, cause);
  }
}
