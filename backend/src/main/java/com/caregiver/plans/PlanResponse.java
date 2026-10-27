package com.caregiver.plans;

import java.time.Instant;
import java.util.UUID;

public record PlanResponse(
    UUID id, UUID recipientId, int version, PlanStatus status, String content,
    String sourceSummaryId, UUID sourcePlanId, String evidenceNoteIds, Instant createdAt,
    PlanSource sourceType, PlanDocument structuredContent, boolean editable) {
  public static PlanResponse from(CarePlan plan, PlanDocument document) {
    return new PlanResponse(plan.getId(), plan.getRecipient().getId(), plan.getVersionNumber(),
        plan.getStatus(), plan.getContent(), plan.getSourceSummaryId(), plan.getSourcePlanId(),
        plan.getEvidenceNoteIds(), plan.getCreatedAt(), plan.getSourceType(), document,
        document != null && plan.getStatus() == PlanStatus.SUGGESTED && plan.getRecipient().isActive()
            && document.changes().stream().anyMatch(change -> !change.params().isEmpty()));
  }
}
