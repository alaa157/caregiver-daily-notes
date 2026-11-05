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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanService {
  private final CarePlanRepository plans;
  private final CareRecipientRepository recipients;
  private final AuditService audit;
  private final PlanDocumentCodec documents;

  public PlanService(CarePlanRepository plans, CareRecipientRepository recipients, AuditService audit, PlanDocumentCodec documents) {
    this.plans = plans;
    this.recipients = recipients;
    this.audit = audit;
    this.documents = documents;
  }

  @Transactional
  public PlanResponse saveSuggested(UUID recipientId, PlanRequest request) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    String content;
    if (request.structuredContent() != null) {
      content = documents.write(request.structuredContent());
    } else {
      if (request.content() == null || request.content().isBlank()) {
        throw new IllegalArgumentException("content must be non-blank");
      }
      content = request.content();
    }
    int version = plans.maxVersionForRecipient(recipientId) + 1;
    CarePlan plan = plans.save(new CarePlan(recipient, version, PlanStatus.SUGGESTED,
        content, request.sourceSummaryId(), null, encodeIds(request.evidenceNoteIds()), PlanSource.CAREGIVER_SUBMITTED));
    audit.record("PLAN_SUGGESTED", "CARE_PLAN", plan.getId().toString(), "version=" + version);
    return response(plan);
  }

  @Transactional(readOnly = true)
  public PlanResponse activeAccepted(UUID recipientId) {
    CareRecipient recipient = ownedRecipient(recipientId);
    return plans.findFirstByRecipient_IdAndStatusInOrderByVersionNumberDesc(recipient.getId(),
        List.of(PlanStatus.ACCEPTED, PlanStatus.EDITED_AND_ACCEPTED))
        .map(this::response).orElseThrow(PlanNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> history(UUID recipientId) {
    CareRecipient recipient = ownedRecipient(recipientId);
    return plans.findAllByRecipient_IdOrderByVersionNumberDesc(recipient.getId()).stream()
        .map(this::response).toList();
  }

  @Transactional
  public PlanResponse accept(UUID recipientId, UUID planId) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan target = ownedPlan(recipient, planId);
    if (target.getStatus() != PlanStatus.SUGGESTED) {
      throw new IllegalPlanTransitionException("Only a suggested plan can be accepted.");
    }
    // Flush archived versions before the replacement claims the unique active-plan key.
    requireCompatibleBaseline(recipient, target);
    archiveActivePlans(recipient.getId(), target.getId());
    target.changeStatus(PlanStatus.ACCEPTED);
    plans.saveAndFlush(target);
    audit.record("PLAN_ACCEPTED", "CARE_PLAN", target.getId().toString(), "version=" + target.getVersionNumber());
    return response(target);
  }

  @Transactional
  public PlanResponse editAndAccept(UUID recipientId, UUID planId, EditPlanRequest request) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan original = ownedPlan(recipient, planId);
    if (original.getStatus() != PlanStatus.SUGGESTED) {
      throw new IllegalPlanTransitionException("Only a suggested plan can be edited and accepted.");
    }
    if (request.expectedVersion() != original.getVersionNumber()) throw new IllegalPlanTransitionException("The source version changed.");
    PlanDocument source = documents.read(original);
    if (source == null || request.changes() == null || request.changes().size() != source.changes().size()) throw new ProtectedPlanException();
    requireCompatibleBaseline(recipient, original);
    List<SuggestedPlanChange> changes = new ArrayList<>(source.changes());
    var seen = new HashSet<Integer>();
    for (PlanChangeEdit edit : request.changes()) {
      if (edit.index() < 0 || edit.index() >= changes.size() || !seen.add(edit.index())) throw new ProtectedPlanException();
      SuggestedPlanChange current = source.changes().get(edit.index());
      if (!edit.params().keySet().equals(current.params().keySet())
          || edit.params().values().stream().anyMatch(value -> value.length() > 500 || value.isBlank())) throw new ProtectedPlanException();
      changes.set(edit.index(), new SuggestedPlanChange(current.action(), edit.params(), current.reason(), current.evidenceNoteIds()));
    }
    if (changes.equals(source.changes())) throw new ProtectedPlanException();
    PlanDocument editedDocument = new PlanDocument(1, changes, source.medications());
    MedicationHardLock.checkUnchanged(source.medications(), editedDocument.medications());
    String content = documents.write(editedDocument);
    int version = plans.maxVersionForRecipient(recipient.getId()) + 1;
    archiveActivePlans(recipient.getId(), null);
    original.changeStatus(PlanStatus.ARCHIVED);
    plans.saveAndFlush(original);
    CarePlan edited = plans.save(new CarePlan(recipient, version, PlanStatus.EDITED_AND_ACCEPTED,
        content, original.getSourceSummaryId(), original.getId(), original.getEvidenceNoteIds(), PlanSource.CAREGIVER_EDITED));
    audit.record("PLAN_EDITED_AND_ACCEPTED", "CARE_PLAN", edited.getId().toString(),
        "version=" + version + ";sourceVersion=" + original.getVersionNumber());
    plans.flush();
    return response(edited);
  }

  @Transactional
  public PlanResponse dismiss(UUID recipientId, UUID planId) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan plan = ownedPlan(recipient, planId);
    plan.changeStatus(PlanStatus.DISMISSED);
    plans.save(plan);
    audit.record("PLAN_DISMISSED", "CARE_PLAN", plan.getId().toString(), "version=" + plan.getVersionNumber());
    return response(plan);
  }

  /** Called only after the server-side AI validator; the current baseline is rechecked under lock. */
  @Transactional
  public PlanResponse saveGenerated(UUID recipientId, SuggestedPlan suggestion, String summaryReference) {
    CareRecipient recipient = ownedRecipientForUpdate(recipientId);
    CarePlan current = currentAccepted(recipientId);
    if (!Objects.equals(suggestion.basedOnPlanId(), current == null ? null : current.getId().toString())) {
      throw new IllegalPlanTransitionException("The accepted baseline changed. Generate a fresh suggestion.");
    }
    PlanDocument baseline = current == null ? null : documents.read(current);
    if (current != null && baseline == null) throw new ProtectedPlanException();
    List<MedicationEntry> medications = baseline == null ? List.of() : baseline.medications();
    MedicationHardLock.checkUnchanged(medications, suggestion.currentMedications());
    MedicationHardLock.checkUnchanged(medications, suggestion.proposedMedications());
    PlanDocument document = new PlanDocument(1, suggestion.changes(), medications);
    List<String> evidence = suggestion.changes().stream().flatMap(change -> change.evidenceNoteIds().stream()).distinct().toList();
    CarePlan saved = plans.saveAndFlush(new CarePlan(recipient, plans.maxVersionForRecipient(recipientId) + 1,
        PlanStatus.SUGGESTED, documents.write(document), summaryReference,
        current == null ? null : current.getId(), encodeIds(evidence), PlanSource.AI_GENERATED));
    audit.record("PLAN_SUGGESTED", "CARE_PLAN", saved.getId().toString(), "version=" + saved.getVersionNumber() + ";source=AI_GENERATED");
    return response(saved);
  }

  private PlanResponse response(CarePlan plan) { return PlanResponse.from(plan, documents.read(plan)); }

  private CarePlan currentAccepted(UUID recipientId) {
    return plans.findFirstByRecipient_IdAndStatusInOrderByVersionNumberDesc(recipientId,
        List.of(PlanStatus.ACCEPTED, PlanStatus.EDITED_AND_ACCEPTED)).orElse(null);
  }

  private void requireCompatibleBaseline(CareRecipient recipient, CarePlan target) {
    CarePlan current = currentAccepted(recipient.getId());
    PlanDocument next = documents.read(target);
    PlanDocument baseline = current == null ? null : documents.read(current);
    if (next == null) { if (baseline != null) throw new ProtectedPlanException(); return; }
    if (current != null && baseline == null) throw new ProtectedPlanException();
    if (!Objects.equals(target.getSourcePlanId(), current == null ? null : current.getId())) {
      throw new IllegalPlanTransitionException("The accepted baseline changed. Generate a fresh suggestion.");
    }
    MedicationHardLock.checkUnchanged(baseline == null ? List.of() : baseline.medications(), next.medications());
  }

  /** Internal service boundary for AI/summary orchestration; never trusts a client-supplied owner. */
  @Transactional(readOnly = true)
  public void requireGenerationAccess(UUID recipientId) {
    if (!ownedRecipient(recipientId).isActive()) throw new InactiveRecipientException();
  }

  @Transactional(readOnly = true)
  public PlanResponse latestAcceptedForAi(UUID recipientId) {
    return activeAccepted(recipientId);
  }

  private void archiveActivePlans(UUID recipientId, UUID exceptId) {
    for (CarePlan current : plans.findAcceptedByRecipient(recipientId,
        List.of(PlanStatus.ACCEPTED, PlanStatus.EDITED_AND_ACCEPTED))) {
      if (exceptId == null || !current.getId().equals(exceptId)) {
        current.changeStatus(PlanStatus.ARCHIVED);
        plans.saveAndFlush(current);
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
