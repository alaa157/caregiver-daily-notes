package com.caregiver.ai;

import com.caregiver.plans.PlanDocument;
import com.caregiver.plans.PlanNotFoundException;
import com.caregiver.plans.PlanResponse;
import com.caregiver.plans.PlanService;
import com.caregiver.plans.ProtectedPlanException;
import com.caregiver.plans.StoredPlanVersion;
import com.caregiver.plans.SuggestedPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PlanGenerationService {
  private final SummaryApplicationService summaries;
  private final PlanService plans;
  private final ProviderDeadline provider;
  private final ObjectMapper mapper;
  public PlanGenerationService(SummaryApplicationService summaries, PlanService plans, ProviderDeadline provider, ObjectMapper mapper) {
    this.summaries = summaries; this.plans = plans; this.provider = provider; this.mapper = mapper;
  }
  public PlanGenerationController.Result generate(SummaryRequest request) {
    plans.requireGenerationAccess(UUID.fromString(request.recipientId()));
    var generated = summaries.generate(request); // Authorization and owned-source loading happen first.
    SummaryResult summary = generated.result();
    if (summary.summary().aiUnavailable()) return unavailable(summary.safety());
    UUID recipientId = UUID.fromString(request.recipientId());
    PlanResponse accepted = null;
    try { accepted = plans.latestAcceptedForAi(recipientId); } catch (PlanNotFoundException noAcceptedPlan) { /* First plan. */ }
    if (accepted != null && accepted.structuredContent() == null) return unavailable(summary.safety());
    PlanDocument baseline = accepted == null ? new PlanDocument(1, List.of(), List.of()) : accepted.structuredContent();
    StoredPlanVersion last = accepted == null ? null : new StoredPlanVersion(accepted.id().toString(),
        new SuggestedPlan(baseline.changes(), baseline.medications(), baseline.medications(), null));
    String reference = fingerprint(summary);
    AtomicReference<PlanResponse> saved = new AtomicReference<>();
    var pipeline = new PlanSuggestionService(provider::complete, (id, suggestion) -> {
      PlanResponse response = plans.saveGenerated(UUID.fromString(id), suggestion, reference);
      saved.set(response);
      return new StoredPlanVersion(response.id().toString(), suggestion);
    });
    try {
      var result = pipeline.suggest(new PlanSuggestionRequest(request.recipientId(), summary.summary(), summary.safety(),
          generated.notes().stream().collect(Collectors.toMap(SummaryNote::noteId, SummaryNote::text)), last, baseline.medications()));
      return result.aiUnavailable() ? unavailable(summary.safety()) : new PlanGenerationController.Result(saved.get(), false, summary.safety());
    } catch (InvalidModelOutputException | ProtectedPlanException invalidSuggestion) { return unavailable(summary.safety()); }
  }
  private PlanGenerationController.Result unavailable(SafetyEvaluation safety) { return new PlanGenerationController.Result(null, true, safety); }
  private String fingerprint(SummaryResult result) {
    try { return "summary-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(result))); }
    catch (Exception serializationFailure) { throw new IllegalStateException("Could not identify the source summary."); }
  }
}
