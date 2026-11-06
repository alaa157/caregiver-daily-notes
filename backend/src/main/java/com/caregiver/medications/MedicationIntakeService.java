package com.caregiver.medications;

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

/** Creates and lists only explicit intake events for caregiver-owned medications. */
@Service
public class MedicationIntakeService {
  private final CareRecipientRepository recipients;
  private final MedicationRepository medications;
  private final MedicationIntakeEventRepository intakeEvents;
  private final EntityManager entityManager;

  public MedicationIntakeService(CareRecipientRepository recipients, MedicationRepository medications,
      MedicationIntakeEventRepository intakeEvents, EntityManager entityManager) {
    this.recipients = recipients;
    this.medications = medications;
    this.intakeEvents = intakeEvents;
    this.entityManager = entityManager;
  }

  @Transactional
  public MedicationIntakeResponse create(UUID recipientId, UUID medicationId, MedicationIntakeRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    CareRecipient recipient = ownedRecipient(recipientId, caregiverId);
    MedicationRecord medication = ownedMedication(medicationId, caregiverId, recipientId);
    MedicationIntakeEvent event = intakeEvents.save(new MedicationIntakeEvent(
        recipient.getCaregiver(), recipient, medication, request.intakeState(),
        request.occurredAt(), request.verificationState()));
    entityManager.flush();
    return response(event);
  }

  @Transactional(readOnly = true)
  public List<MedicationIntakeResponse> list(UUID recipientId, UUID medicationId) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    ownedRecipient(recipientId, caregiverId);
    ownedMedication(medicationId, caregiverId, recipientId);
    return intakeEvents
        .findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
            caregiverId, recipientId, medicationId)
        .stream()
        .map(this::response)
        .toList();
  }

  private CareRecipient ownedRecipient(UUID recipientId, UUID caregiverId) {
    return recipients.findByIdAndCaregiver_Id(recipientId, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }

  private MedicationRecord ownedMedication(UUID medicationId, UUID caregiverId, UUID recipientId) {
    return medications.findByIdAndCaregiver_IdAndRecipient_Id(medicationId, caregiverId, recipientId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found."));
  }

  private MedicationIntakeResponse response(MedicationIntakeEvent event) {
    return new MedicationIntakeResponse(
        event.getId(),
        event.getMedication().getId(),
        event.getRecipient().getId(),
        event.getIntakeState(),
        event.getOccurredAt(),
        event.getVerificationState(),
        event.getCreatedAt());
  }
}
