package com.caregiver.ai;

import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.PlanAction;
import com.caregiver.plans.PlanDocument;
import com.caregiver.plans.PlanTextSafety;
import com.caregiver.plans.ProtectedPlanException;
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
 * the provider, strictly parses, resolves catalog actions, checks evidence,
 * and only then persists. Any failure throws {@link InvalidModelOutputException}
 * before the store is touched; provider fallback yields an explicit unavailable
 * result with zero store calls.
 *
 * <p>Medication values are server-owned. A model-supplied medication echo is
 * accepted for parser tolerance but is never validated or persisted: the saved
 * suggestion always carries the baseline medication list (issue #751).</p>
 */
public class PlanSuggestionService {

  private static final String PLAN_SYSTEM_PROMPT_PREFIX =
      "You suggest care-plan updates from grounded summaries. Select ONLY actions from the fixed catalog "
          + "with its exact parameter names. Cite evidenceNoteIds from the supplied notes for every change "
          + "and give a reason. NEVER add, remove, or change medications, doses, or schedules. "
          + "NEVER diagnose or prescribe. Return ONLY valid JSON with exactly this shape: "
          + "{\"changes\":[{\"actionId\":\"<catalog action id>\",\"params\":{\"<parameter name>\":\"<value>\"},"
          + "\"reason\":\"<short reason>\",\"evidenceNoteIds\":[\"<note id>\"]}]}. "
          + "The top-level key MUST be \"changes\". Each change MUST use the key \"params\" (never \"parameters\"). "
          + "Use an empty changes array when no update is warranted. ";
  static final String PLAN_SYSTEM_PROMPT = systemPrompt(AiOutputLanguage.NOTES);

  static record RawChange(String actionId, @com.fasterxml.jackson.annotation.JsonAlias("parameters") Map<String, String> params, String reason, List<String> evidenceNoteIds) {
  }

  /**
   * Shape of model output. {@code proposedMedications} is retained only so the
   * strict parser still tolerates models that echo the medication list; its
   * value is never read. Medications on the saved plan always come from the
   * server-owned baseline (issue #751).
   */
  static record ParsedSuggestion(List<RawChange> changes, List<MedicationEntry> proposedMedications) {
  }

  private final LlmClient llm;
  private final SuggestedPlanStore store;

  public PlanSuggestionService(LlmClient llm, SuggestedPlanStore store) {
    this.llm = Objects.requireNonNull(llm, "llm");
    this.store = Objects.requireNonNull(store, "store");
  }

  public PlanSuggestionResult suggest(PlanSuggestionRequest request) {
    return suggest(request, null);
  }

  public PlanSuggestionResult suggest(PlanSuggestionRequest request, ProviderCredentials credentials) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    LlmResult result = llm.complete(new LlmRequest(
        systemPrompt(request.language()), renderInput(request), credentials));
    if (result.status() == LlmStatus.FALLBACK) {
      return new PlanSuggestionResult(null, true);
    }
    // The medication echo is no longer required: the model is not asked to
    // produce one and its value is ignored. "changes" stays required.
    ParsedSuggestion parsed = StrictJsonParser.parse(
        result.text(), ParsedSuggestion.class, Set.of("changes"));
    List<SuggestedPlanChange> changes = new ArrayList<>();
    List<RawChange> rawChanges = parsed.changes() != null ? parsed.changes() : List.of();
    int removedUnsafeChanges = 0;
    for (int i = 0; i < rawChanges.size(); i++) {
      if (unsafeChange(rawChanges.get(i))) {
        removedUnsafeChanges++;
        continue;
      }
      changes.add(toChange(i, rawChanges.get(i)));
    }
    if (!rawChanges.isEmpty() && changes.isEmpty()) {
      // Every generated change carried unsafe text: there is no usable plan left.
      throw new InvalidModelOutputException(
          "All generated changes contain unsafe plan text", PlanFailureCodes.TEXT_SAFETY);
    }
    for (int i = 0; i < changes.size(); i++) {
      PlanEvidenceValidator.validateNoteIds(
          changes.get(i).evidenceNoteIds(), request.noteTextsById().keySet(), i);
    }
    try {
      new PlanDocument(1, changes, List.of());
    } catch (ProtectedPlanException invalidDocument) {
      throw new InvalidModelOutputException(
          "Generated plan contains invalid catalog actions", PlanFailureCodes.PROTECTED_PLAN, invalidDocument);
    }
    String basedOn = request.lastAccepted() != null ? request.lastAccepted().versionId() : null;
    // Both medication fields come from the server-owned baseline. The model's
    // echo is discarded, so it can no longer fail generation (issue #751).
    SuggestedPlan plan =
        new SuggestedPlan(changes, request.currentMedications(), request.currentMedications(), basedOn);
    return new PlanSuggestionResult(store.save(request.recipientId(), plan), false, removedUnsafeChanges);
  }

  /** Safety is enforced per change so one bad phrase does not discard the whole plan. */
  private static boolean unsafeChange(RawChange raw) {
    if (raw == null) {
      return false;
    }
    if (PlanTextSafety.isUnsafe(raw.reason())) {
      return true;
    }
    if (raw.params() != null) {
      for (String value : raw.params().values()) {
        if (PlanTextSafety.isUnsafe(value)) {
          return true;
        }
      }
    }
    return false;
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

  static String systemPrompt(String language) {
    return PLAN_SYSTEM_PROMPT_PREFIX
        + "Write each change reason and every human-readable parameter value in "
        + AiOutputLanguage.promptLanguage(language)
        + ". Keep catalog action IDs, parameter names, evidence note IDs, numbers, and times unchanged.";
  }
}
