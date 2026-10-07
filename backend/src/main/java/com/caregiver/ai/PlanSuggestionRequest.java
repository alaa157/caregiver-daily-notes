package com.caregiver.ai;

import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.StoredPlanVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Input for one suggestion call: the grounded summary, authoritative
 * safety, citable note texts, the previously accepted version (or null for
 * a first plan), and the current medication section for hard-locking.
 */
public record PlanSuggestionRequest(
    String recipientId,
    GroundedSummary summary,
    SafetyEvaluation safety,
    Map<String, String> noteTextsById,
    StoredPlanVersion lastAccepted,
    List<MedicationEntry> currentMedications) {

  public PlanSuggestionRequest {
    if (recipientId == null || recipientId.isBlank()) {
      throw new IllegalArgumentException("recipientId must be non-blank");
    }
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(safety, "safety");
    Objects.requireNonNull(noteTextsById, "noteTextsById");
    noteTextsById = Map.copyOf(noteTextsById);
    Objects.requireNonNull(currentMedications, "currentMedications");
    currentMedications = List.copyOf(currentMedications);
  }
}
