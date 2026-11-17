package com.caregiver.medications;

import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Creates and lists only explicit intake events for caregiver-owned medications. */
@Service
public class MedicationIntakeService {
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final CareRecipientRepository recipients;
  private final MedicationRepository medications;
  private final MedicationIntakeEventRepository intakeEvents;
  private final EntityManager entityManager;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public MedicationIntakeService(CareRecipientRepository recipients, MedicationRepository medications,
      MedicationIntakeEventRepository intakeEvents, EntityManager entityManager,
      PlatformTransactionManager transactionManager) {
    this.recipients = recipients;
    this.medications = medications;
    this.intakeEvents = intakeEvents;
    this.entityManager = entityManager;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  /**
   * Records an explicit intake event. An {@code Idempotency-Key} makes a retry
   * safe: the same key replays the original event instead of recording a second
   * dose. The database unique constraint on {@code (caregiver_id,
   * idempotency_key)} settles concurrent duplicates and the loser reads the
   * winner's committed row. Keys are scoped to the caregiver, never to the
   * timestamp, so two legitimate doses can still share an occurred-at.
   */
  public MedicationIntakeResponse create(UUID recipientId, UUID medicationId,
      MedicationIntakeRequest request, String key) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    String idempotencyKey = normalizeKey(key);
    if (idempotencyKey != null) {
      MedicationIntakeResponse replay = readTx.execute(status -> intakeEvents
          .findByCaregiver_IdAndIdempotencyKey(caregiverId, idempotencyKey)
          .map(this::response)
          .orElse(null));
      if (replay != null) {
        return replay;
      }
    }
    try {
      return writeTx.execute(status -> {
        if (idempotencyKey != null) {
          var existing = intakeEvents.findByCaregiver_IdAndIdempotencyKey(caregiverId, idempotencyKey);
          if (existing.isPresent()) {
            return response(existing.get());
          }
        }
        CareRecipient recipient = ownedRecipient(recipientId, caregiverId);
        MedicationRecord medication = ownedMedication(medicationId, caregiverId, recipientId);
        MedicationIntakeEvent event = intakeEvents.save(new MedicationIntakeEvent(
            recipient.getCaregiver(), recipient, medication, request.intakeState(),
            request.occurredAt(), request.verificationState(), idempotencyKey));
        entityManager.flush();
        return response(event);
      });
    } catch (DataIntegrityViolationException e) {
      return recoverCreate(caregiverId, idempotencyKey, e);
    }
  }

  /**
   * Resolves a write that lost the unique-constraint race. Runs in a fresh read
   * transaction so it always observes the winner's committed row.
   */
  private MedicationIntakeResponse recoverCreate(UUID caregiverId, String idempotencyKey,
      DataIntegrityViolationException failure) {
    return readTx.execute(status -> {
      if (idempotencyKey != null) {
        var existing = intakeEvents.findByCaregiver_IdAndIdempotencyKey(caregiverId, idempotencyKey);
        if (existing.isPresent()) {
          return response(existing.get());
        }
      }
      throw failure;
    });
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new IllegalArgumentException("Idempotency-Key must be 1-100 characters.");
    }
    return key;
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
