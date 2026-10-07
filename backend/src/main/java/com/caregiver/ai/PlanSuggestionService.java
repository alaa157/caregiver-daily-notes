package com.caregiver.ai;

import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.MedicationHardLock;
import com.caregiver.plans.PlanAction;
import com.caregiver.plans.SuggestedPlan;
import com.caregiver.plans.SuggestedPlanChange;
import com.caregiver.plans.SuggestedPlanStore;
import com.caregiver.plans.StoredPlanVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed plan suggestion orchestration: renders grounded input, calls
 * the provider, strictly parses, resolves catalog actions, checks evidence
 * and the medication hard lock, and only then persists. Any failure throws
 * {@link InvalidModelOutputException} before the store is touched; provider
 * fallback yields an explicit unavailable result with zero store calls.
 */
public class PlanSuggestionService {

  static final String PLAN_SYSTEM_PROMPT =
      "You suggest care-plan updates from grounded summaries. Select ONLY actions from the fixed catalog "
          + "with its exact parameter names. Cite evidenceNoteIds from the supplied notes for every change "
          + "and give a reason. NEVER add, remove, or change medications, doses, or schedules; repeat the "
          + "current medication section exactly. NEVER diagnose or prescribe. Return ONLY valid JSON.";

  static record RawChange(String actionId, Map<String, String> params, String reason, List<String> evidenceNoteIds) {
  }

  static record ParsedSuggestion(List<RawChange> changes, List<MedicationEntry> proposedMedications) {
  }

  private final LlmClient llm;
  private final SuggestedPlanStore store;

  public PlanSuggestionService(LlmClient llm, SuggestedPlanStore store) {
    this.llm = Objects.requireNonNull(llm, "llm");
    this.store = Objects.requireNonNull(store, "store");
  }

  public PlanSuggestionResult suggest(PlanSuggestionRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    LlmResult result = llm.complete(new LlmRequest(PLAN_SYSTEM_PROMPT, renderInput(request)));
    if (result.status() == LlmStatus.FALLBACK) {
      return new PlanSuggestionResult(null, true);
    }
    ParsedSuggestion parsed = StrictJsonParser.parse(
        result.text(), ParsedSuggestion.class, Set.of("changes", "proposedMedications"));
    List<SuggestedPlanChange> changes = new ArrayList<>();
    List<RawChange> rawChanges = parsed.changes() != null ? parsed.changes() : List.of();
    for (int i = 0; i < rawChanges.size(); i++) {
      changes.add(toChange(i, rawChanges.get(i)));
    }
    List<MedicationEntry> proposed =
        parsed.proposedMedications() != null ? parsed.proposedMedications() : List.of();
    for (int i = 0; i < changes.size(); i++) {
      for (String noteId : changes.get(i).evidenceNoteIds()) {
        if (!request.noteTextsById().containsKey(noteId)) {
          throw new InvalidModelOutputException("Change " + i + " cites unknown note: " + noteId);
        }
      }
    }
    String basedOn = request.lastAccepted() != null ? request.lastAccepted().versionId() : null;
    SuggestedPlan plan =
        new SuggestedPlan(changes, request.currentMedications(), proposed, basedOn);
    MedicationHardLock.checkUnchanged(request.currentMedications(), plan.proposedMedications());
    return new PlanSuggestionResult(store.save(plan), false);
  }

  private static SuggestedPlanChange toChange(int index, RawChange raw) {
    if (raw == null) {
      throw new InvalidModelOutputException("Change " + index + " is missing");
    }
    PlanAction action = null;
    for (PlanAction candidate : PlanAction.values()) {
      if (candidate.getId().equals(raw.actionId())) {
        action = candidate;
        break;
      }
    }
    if (action == null) {
      throw new InvalidModelOutputException("Change " + index + " uses unknown action: " + raw.actionId());
    }
    try {
      return new SuggestedPlanChange(action, raw.params(), raw.reason(), raw.evidenceNoteIds());
    } catch (IllegalArgumentException e) {
      throw new InvalidModelOutputException("Change " + index + " is invalid: " + e.getMessage(), e);
    }
  }

  static String renderInput(PlanSuggestionRequest request) {
    StringBuilder input = new StringBuilder();
    input.append("SUMMARY:\n");
    for (SummaryObservation observation : request.summary().observations()) {
      input.append("- ").append(observation.text()).append(" [").append(observation.noteId()).append("]\n");
    }
    for (SummaryUncertainty uncertainty : request.summary().uncertainties()) {
      input.append("- UNCERTAIN: ").append(uncertainty.topic()).append(": ").append(uncertainty.detail()).append("\n");
    }
    input.append("SAFETY:\n");
    if (request.safety().signals().isEmpty()) {
      input.append("none\n");
    } else {
      for (SafetySignal signal : request.safety().signals()) {
        input.append("- ").append(signal.ruleId().getId()).append("/").append(signal.severity()).append("\n");
      }
    }
    input.append("LAST ACCEPTED: ");
    if (request.lastAccepted() == null) {
      input.append("none\n");
    } else {
      input.append(request.lastAccepted().versionId()).append(" actions [");
      List<String> actions = new ArrayList<>();
      for (SuggestedPlanChange change : request.lastAccepted().plan().changes()) {
        actions.add(change.action().getId());
      }
      input.append(String.join(",", actions)).append("]\n");
    }
    input.append("CURRENT MEDS:\n");
    for (MedicationEntry med : request.currentMedications()) {
      input.append("- ").append(med.name()).append("|").append(med.dose()).append("|").append(med.schedule()).append("\n");
    }
    input.append("CATALOG:\n");
    for (PlanAction action : PlanAction.values()) {
      input.append("- ").append(action.getId()).append("(").append(String.join(",", action.allowedParams())).append(")\n");
    }
    return input.toString();
  }
}
