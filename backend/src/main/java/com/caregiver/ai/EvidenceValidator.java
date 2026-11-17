package com.caregiver.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed evidence gate. Every observation must cite a supplied note id
 * and carry a substantive quote; violations throw
 * {@link InvalidModelOutputException} naming the observation index. The
 * normalized-substring quote check remains the final gate, but a quote must
 * first clear a length floor and must not be made only of the serialization
 * scaffolding that appears in the note text (field keys, state markers and
 * structural keys). Cross-note pattern claims must cite at least two distinct
 * notes, each with its own quote.
 */
public final class EvidenceValidator {

  /** Minimum normalized characters in a quote before it can count as evidence. */
  public static final int MIN_QUOTE_LENGTH = 8;
  /** A single word or label is never evidence, however long it is. */
  public static final int MIN_QUOTE_WORDS = 2;
  /** Minimum distinct sources for a cross-note pattern claim. */
  public static final int MIN_PATTERN_SOURCES = 2;

  private static final Set<String> SCAFFOLDING_TOKENS = Set.copyOf(
      SummarySourceLoader.NON_EVIDENCE_TOKENS.stream()
          .map(token -> token.toLowerCase(Locale.ROOT))
          .toList());

  private EvidenceValidator() {
  }

  public static GroundedSummary validate(GroundedSummary summary, Map<String, String> noteTextsById) {
    if (summary == null || noteTextsById == null) {
      throw new IllegalArgumentException("summary and noteTextsById must be non-null");
    }
    List<SummaryObservation> observations = summary.observations();
    Map<String, String> normalized = new HashMap<>();
    for (Map.Entry<String, String> entry : noteTextsById.entrySet()) {
      normalized.put(entry.getKey(), ArabicNormalizer.normalize(entry.getValue()));
    }
    for (int i = 0; i < observations.size(); i++) {
      SummaryObservation observation = observations.get(i);
      if (observation.hasPatternCitations()) {
        validatePattern(i, observation, normalized);
      } else {
        validateCitation(i, observation.noteId(), observation.quote(), normalized);
      }
    }
    return summary;
  }

  private static void validatePattern(int index, SummaryObservation observation,
      Map<String, String> normalized) {
    List<String> noteIds = observation.noteIds();
    List<String> quotes = observation.quotes();
    if (noteIds == null || quotes == null || noteIds.isEmpty()
        || noteIds.size() != quotes.size()
        || noteIds.size() < MIN_PATTERN_SOURCES
        || new HashSet<>(noteIds).size() < MIN_PATTERN_SOURCES) {
      throw new InvalidModelOutputException("Observation " + index + " pattern claim must cite at least "
          + MIN_PATTERN_SOURCES + " distinct notes with one quote each");
    }
    for (int source = 0; source < noteIds.size(); source++) {
      validateCitation(index, noteIds.get(source), quotes.get(source), normalized);
    }
  }

  private static void validateCitation(int index, String noteId, String rawQuote,
      Map<String, String> normalized) {
    if (!normalized.containsKey(noteId)) {
      throw new InvalidModelOutputException("Observation " + index + " cites unknown note: " + noteId);
    }
    if (rawQuote == null || rawQuote.isBlank()) {
      throw new InvalidModelOutputException("Observation " + index + " has empty quote");
    }
    String quote = ArabicNormalizer.normalize(rawQuote).trim();
    if (isScaffoldingOnly(quote)) {
      throw new InvalidModelOutputException("Observation " + index + " quote is a field label or state marker");
    }
    if (quote.length() < MIN_QUOTE_LENGTH || quote.split("\\s+").length < MIN_QUOTE_WORDS) {
      throw new InvalidModelOutputException("Observation " + index + " quote is too short to be evidence");
    }
    if (!normalized.get(noteId).contains(quote)) {
      throw new InvalidModelOutputException("Observation " + index + " quote not found in note: " + noteId);
    }
  }

  private static boolean isScaffoldingOnly(String quote) {
    for (String token : quote.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (!token.isEmpty() && !SCAFFOLDING_TOKENS.contains(token)) {
        return false;
      }
    }
    return true;
  }
}
