package com.caregiver.medications;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated caregiver- and recipient-scoped medication operations. */
@Service
public class MedicationService {
  private final MedicationRepository medications;
  private final CareRecipientRepository recipients;
  private final EntityManager entityManager;
  private final AuditService audit;

  public MedicationService(MedicationRepository medications, CareRecipientRepository recipients,
      EntityManager entityManager, AuditService audit) {
    this.medications = medications;
    this.recipients = recipients;
    this.entityManager = entityManager;
    this.audit = audit;
  }

  @Transactional
  public MedicationResponse create(UUID recipientId, MedicationRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipientForUpdate(recipientId, caregiverId);
    MedicationLifecycle.requireActiveRecipient(recipient);
    MedicationRecord record = medications.save(new MedicationRecord(recipient.getCaregiver(), recipient,
        request.displayName(), request.doseText(), request.scheduleText()));
    entityManager.flush();
    audit.record("MEDICATION_CREATED", "MEDICATION", record.getId().toString(), null);
    return MedicationResponse.from(record);
  }

  @Transactional(readOnly = true)
  public List<MedicationResponse> list(UUID recipientId) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipient(recipientId, caregiverId);
    return medications.findAllByCaregiver_IdAndRecipient_IdOrderByUpdatedAtDescIdAsc(
            caregiverId, recipient.getId())
        .stream().map(MedicationResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public MedicationResponse get(UUID recipientId, UUID medicationId) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipient(recipientId, caregiverId);
    return ownedMedication(medicationId, caregiverId, recipient.getId()).map(MedicationResponse::from)
        .orElseThrow(MedicationService::medicationNotFound);
  }

  @Transactional
  public MedicationResponse update(UUID recipientId, UUID medicationId, MedicationRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipientForUpdate(recipientId, caregiverId);
    MedicationLifecycle.requireActiveRecipient(recipient);
    MedicationRecord record = ownedMedication(medicationId, caregiverId, recipient.getId())
        .orElseThrow(MedicationService::medicationNotFound);
    MedicationLifecycle.requireActiveMedication(record, "updated");
    record.updateDetails(request.displayName(), request.doseText(), request.scheduleText());
    entityManager.flush();
    audit.record("MEDICATION_UPDATED", "MEDICATION", record.getId().toString(), null);
    return MedicationResponse.from(record);
  }

  @Transactional
  public MedicationResponse archive(UUID recipientId, UUID medicationId) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipientForUpdate(recipientId, caregiverId);
    MedicationLifecycle.requireActiveRecipient(recipient);
    MedicationRecord record = ownedMedication(medicationId, caregiverId, recipient.getId())
        .orElseThrow(MedicationService::medicationNotFound);
    boolean wasActive = record.isActive();
    record.archive();
    entityManager.flush();
    if (wasActive) {
      audit.record("MEDICATION_ARCHIVED", "MEDICATION", record.getId().toString(), null);
    }
    return MedicationResponse.from(record);
  }

  private CareRecipient ownedRecipient(UUID recipientId, UUID caregiverId) {
    return recipients.findByIdAndCaregiver_Id(recipientId, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }

  private CareRecipient ownedRecipientForUpdate(UUID recipientId, UUID caregiverId) {
    return recipients.findOwnedForUpdate(recipientId, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }

  private java.util.Optional<MedicationRecord> ownedMedication(UUID medicationId, UUID caregiverId,
      UUID recipientId) {
    return medications.findByIdAndCaregiver_IdAndRecipient_Id(medicationId, caregiverId, recipientId);
  }

  private static ResponseStatusException medicationNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found.");
  }
}
