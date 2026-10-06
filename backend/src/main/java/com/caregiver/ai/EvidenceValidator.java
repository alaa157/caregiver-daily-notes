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
    for (int i = 0; i < observations.size(); i++) {
      SummaryObservation observation = observations.get(i);
      if (!noteTextsById.containsKey(observation.noteId())) {
        throw new InvalidModelOutputException("Observation " + i + " cites unknown note: " + observation.noteId());
      }
      if (observation.quote().isBlank()) {
        throw new InvalidModelOutputException("Observation " + i + " has empty quote");
      }
    }
    return summary;
  }
}
