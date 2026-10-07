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
    if (params == null) {
      throw new IllegalArgumentException("params must be non-null");
    }
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (entry.getKey() == null || !action.allowedParams().contains(entry.getKey())) {
        throw new IllegalArgumentException("params must use only " + action.allowedParams());
      }
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("param values must be non-null");
      }
      PlanTextSafety.requireSafe(entry.getValue());
    }
    params = Map.copyOf(params);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must be non-blank");
    }
    PlanTextSafety.requireSafe(reason);
    if (evidenceNoteIds == null || evidenceNoteIds.isEmpty()) {
      throw new IllegalArgumentException("at least one evidence note id is required");
    }
    if (evidenceNoteIds.stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new IllegalArgumentException("evidence note ids must be non-blank");
    }
    evidenceNoteIds = List.copyOf(evidenceNoteIds);
  }
}
