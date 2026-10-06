package com.caregiver.ai;

import java.util.List;
import java.util.Map;

/**
 * Fail-closed evidence gate. Every observation must cite a supplied note id
 * and carry a non-empty quote; violations throw
 * {@link InvalidModelOutputException} naming the observation index. The
 * normalized-substring quote check is applied on top of these checks.
 */
public final class EvidenceValidator {

  private EvidenceValidator() {
  }

  public static GroundedSummary validate(GroundedSummary summary, Map<String, String> noteTextsById) {
    if (summary == null || noteTextsById == null) {
      throw new IllegalArgumentException("summary and noteTextsById must be non-null");
    }
    List<SummaryObservation> observations = summary.observations();
    Map<String, String> normalized = new java.util.HashMap<>();
    for (Map.Entry<String, String> entry : noteTextsById.entrySet()) {
      normalized.put(entry.getKey(), ArabicNormalizer.normalize(entry.getValue()));
    }
    for (int i = 0; i < observations.size(); i++) {
      SummaryObservation observation = observations.get(i);
      if (!normalized.containsKey(observation.noteId())) {
        throw new InvalidModelOutputException("Observation " + i + " cites unknown note: " + observation.noteId());
      }
      if (observation.quote().isBlank()) {
        throw new InvalidModelOutputException("Observation " + i + " has empty quote");
      }
      String quote = ArabicNormalizer.normalize(observation.quote());
      if (quote.isEmpty() || !normalized.get(observation.noteId()).contains(quote)) {
        throw new InvalidModelOutputException("Observation " + i + " quote not found in note: " + observation.noteId());
      }
    }
    return summary;
  }
}
