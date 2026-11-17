package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One grounded claim: human-readable text backed by verbatim quotes from the
 * cited note(s). Quotes are validated by {@link EvidenceValidator}, never
 * trusted on arrival.
 *
 * <p>A single-fact claim uses {@code noteId}/{@code quote}. A cross-note
 * pattern claim uses {@code noteIds}/{@code quotes} and must cite at least two
 * distinct notes. The list fields are optional so existing single-citation
 * output stays valid.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryObservation(
    String text,
    String noteId,
    String quote,
    List<String> noteIds,
    List<String> quotes) {

  public SummaryObservation(String text, String noteId, String quote) {
    this(text, noteId, quote, null, null);
  }

  public SummaryObservation {
    if (text == null || noteId == null) {
      throw new IllegalArgumentException("text and noteId must be non-null");
    }
    if (quote == null) {
      quote = "";
    }
  }

  public boolean hasPatternCitations() {
    return noteIds != null || quotes != null;
  }
}
