package com.caregiver.ai;

/**
 * One grounded claim: human-readable text backed by a verbatim quote from
 * the cited note. Quotes are validated by {@link EvidenceValidator}, never
 * trusted on arrival.
 */
public record SummaryObservation(String text, String noteId, String quote) {

  public SummaryObservation {
    if (text == null || noteId == null) {
      throw new IllegalArgumentException("text and noteId must be non-null");
    }
    if (quote == null) {
      quote = "";
    }
  }
}
