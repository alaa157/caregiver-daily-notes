package com.caregiver.plans;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import com.caregiver.recipient.InactiveRecipientException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanService {
  private final CarePlanRepository plans;
  private final CareRecipientRepository recipients;
  private final AuditService audit;

  public PlanService(CarePlanRepository plans, CareRecipientRepository recipients, AuditService audit) {
    this.plans = plans;
    this.recipients = recipients;
    this.audit = audit;
  }

  @Transactional
  public PlanResponse saveSuggested(UUID recipientId, PlanRequest request) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    int version = plans.maxVersionForRecipient(recipientId) + 1;
    CarePlan plan = plans.save(new CarePlan(recipient, version, PlanStatus.SUGGESTED,
        request.content(), request.sourceSummaryId(), null, encodeIds(request.evidenceNoteIds())));
    audit.record("PLAN_SUGGESTED", "CARE_PLAN", plan.getId().toString(), "version=" + version);
    return PlanResponse.from(plan);
  }

  @Transactional(readOnly = true)
  public PlanResponse activeAccepted(UUID recipientId) {
    CareRecipient recipient = ownedRecipient(recipientId);
    return plans.findFirstByRecipient_IdAndStatusInOrderByVersionNumberDesc(recipient.getId(),
        List.of(PlanStatus.ACCEPTED, PlanStatus.EDITED_AND_ACCEPTED))
        .map(PlanResponse::from).orElseThrow(PlanNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> history(UUID recipientId) {
    CareRecipient recipient = ownedRecipient(recipientId);
    return plans.findAllByRecipient_IdOrderByVersionNumberDesc(recipient.getId()).stream()
        .map(PlanResponse::from).toList();
  }

  @Transactional
  public PlanResponse accept(UUID recipientId, UUID planId) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan target = ownedPlan(recipient, planId);
    if (target.getStatus() != PlanStatus.SUGGESTED) {
      throw new IllegalPlanTransitionException("Only a suggested plan can be accepted.");
    }
    archiveActivePlans(recipient.getId(), target.getId());
    target.changeStatus(PlanStatus.ACCEPTED);
    plans.save(target);
    audit.record("PLAN_ACCEPTED", "CARE_PLAN", target.getId().toString(), "version=" + target.getVersionNumber());
    return PlanResponse.from(target);
  }

  @Transactional
  public PlanResponse editAndAccept(UUID recipientId, UUID planId, EditPlanRequest request) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan original = ownedPlan(recipient, planId);
    if (original.getStatus() != PlanStatus.SUGGESTED) {
      throw new IllegalPlanTransitionException("Only a suggested plan can be edited and accepted.");
    }
    int version = plans.maxVersionForRecipient(recipient.getId()) + 1;
    archiveActivePlans(recipient.getId(), null);
    original.changeStatus(PlanStatus.ARCHIVED);
    plans.save(original);
    CarePlan edited = plans.save(new CarePlan(recipient, version, PlanStatus.EDITED_AND_ACCEPTED,
        request.content(), request.sourceSummaryId() == null ? original.getSourceSummaryId() : request.sourceSummaryId(),
        original.getId(), request.evidenceNoteIds().isEmpty()
            ? original.getEvidenceNoteIds() : encodeIds(request.evidenceNoteIds())));
    audit.record("PLAN_EDITED_AND_ACCEPTED", "CARE_PLAN", edited.getId().toString(),
        "version=" + version + ";sourceVersion=" + original.getVersionNumber());
    return PlanResponse.from(edited);
  }

  @Transactional
  public PlanResponse dismiss(UUID recipientId, UUID planId) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan plan = ownedPlan(recipient, planId);
    plan.changeStatus(PlanStatus.DISMISSED);
    plans.save(plan);
    audit.record("PLAN_DISMISSED", "CARE_PLAN", plan.getId().toString(), "version=" + plan.getVersionNumber());
    return PlanResponse.from(plan);
  }

  /** Internal service boundary for AI/summary orchestration; never trusts a client-supplied owner. */
  @Transactional(readOnly = true)
  public PlanResponse latestAcceptedForAi(UUID recipientId) {
    return activeAccepted(recipientId);
  }

  private void archiveActivePlans(UUID recipientId, UUID exceptId) {
    for (CarePlan current : plans.findAcceptedByRecipient(recipientId,
        List.of(PlanStatus.ACCEPTED, PlanStatus.EDITED_AND_ACCEPTED))) {
      if (exceptId == null || !current.getId().equals(exceptId)) {
        current.changeStatus(PlanStatus.ARCHIVED);
        plans.save(current);
        audit.record("PLAN_ARCHIVED", "CARE_PLAN", current.getId().toString(),
            "version=" + current.getVersionNumber());
      }
    }
  }

  private CareRecipient ownedRecipient(UUID recipientId) {
    return recipients.findByIdAndCaregiver_Id(recipientId, AuthContext.currentCaregiverId())
        .orElseThrow(RecipientNotFoundException::new);
  }

  private CareRecipient ownedRecipientForUpdate(UUID recipientId) {
    CareRecipient recipient = recipients.findOwnedForUpdate(recipientId, AuthContext.currentCaregiverId())
        .orElseThrow(RecipientNotFoundException::new);
    if (!recipient.isActive()) {
      throw new InactiveRecipientException();
    }
    return recipient;
  }

  private CarePlan ownedPlan(CareRecipient recipient, UUID planId) {
    return plans.findByIdAndRecipient_Id(planId, recipient.getId())
        .orElseThrow(PlanNotFoundException::new);
  }

  private String encodeIds(List<String> ids) {
    return ids == null || ids.isEmpty() ? null : ids.stream().collect(Collectors.joining(","));
  }
}
