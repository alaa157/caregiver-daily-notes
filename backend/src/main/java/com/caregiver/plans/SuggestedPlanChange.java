package com.caregiver.plans;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One proposed plan change: a catalog action with exact-shema params, a
 * human-readable reason, and the note IDs evidencing it.
 */
public record SuggestedPlanChange(
    PlanAction action, Map<String, String> params, String reason, List<String> evidenceNoteIds) {

  public SuggestedPlanChange {
    Objects.requireNonNull(action, "action");
    if (params == null || !action.allowedParams().containsAll(params.keySet())) {
      throw new IllegalArgumentException("params must use only " + action.allowedParams());
    }
    params = Map.copyOf(params);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must be non-blank");
    }
    if (evidenceNoteIds == null || evidenceNoteIds.isEmpty()) {
      throw new IllegalArgumentException("at least one evidence note id is required");
    }
    evidenceNoteIds = List.copyOf(evidenceNoteIds);
  }
}
