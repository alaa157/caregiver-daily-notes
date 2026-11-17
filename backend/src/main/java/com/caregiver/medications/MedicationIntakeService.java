package com.caregiver.medications;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.IdempotencyConflictException;
import com.caregiver.recipient.RecipientNotFoundException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  // A small allowance for clock skew between the caregiver's device and the
  // server; anything later than that has not happened yet.
  private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
  // Intake records older than a year are not plausible caregiver backfills.
  private static final Duration MAX_PAST_AGE = Duration.ofDays(365);

  private final CareRecipientRepository recipients;
  private final MedicationRepository medications;
  private final MedicationIntakeEventRepository intakeEvents;
  private final EntityManager entityManager;
  private final AuditService audit;
  private final Clock clock;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public MedicationIntakeService(CareRecipientRepository recipients, MedicationRepository medications,
      MedicationIntakeEventRepository intakeEvents, EntityManager entityManager, AuditService audit,
      Clock clock, PlatformTransactionManager transactionManager) {
    this.recipients = recipients;
    this.medications = medications;
    this.intakeEvents = intakeEvents;
    this.entityManager = entityManager;
    this.audit = audit;
    this.clock = clock;
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
    validateOccurredAt(request.occurredAt());
    String idempotencyKey = normalizeKey(key);
    if (idempotencyKey != null) {
      MedicationIntakeResponse replay = readTx.execute(status -> intakeEvents
          .findByCaregiver_IdAndIdempotencyKey(caregiverId, idempotencyKey)
          .map(existing -> replay(recipientId, medicationId, request, existing))
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
            return replay(recipientId, medicationId, request, existing.get());
          }
        }
        CareRecipient recipient = ownedRecipient(recipientId, caregiverId);
        MedicationRecord medication = ownedMedication(medicationId, caregiverId, recipientId);
        MedicationLifecycle.requireActiveRecipient(recipient);
        MedicationLifecycle.requireActiveMedication(medication, "used for intake");
        MedicationIntakeEvent event = intakeEvents.save(new MedicationIntakeEvent(
            recipient.getCaregiver(), recipient, medication, request.intakeState(),
            request.occurredAt(), request.verificationState(), idempotencyKey));
        entityManager.flush();
        // Identifiers only: never copy the dose, state or time into the audit payload.
        audit.record("MEDICATION_INTAKE_RECORDED", "MEDICATION_INTAKE_EVENT", event.getId().toString(),
            "medicationId=" + medication.getId());
        return response(event);
      });
    } catch (DataIntegrityViolationException e) {
      return recoverCreate(caregiverId, recipientId, medicationId, request, idempotencyKey, e);
    }
  }

  /**
   * Replays the original event only when the request is identical. A reused key
   * with different recipient, medication, state, time or verification is a
   * conflict, never a silent overwrite of the first dose record.
   */
  private MedicationIntakeResponse replay(UUID recipientId, UUID medicationId,
      MedicationIntakeRequest request, MedicationIntakeEvent existing) {
    if (!sameRequest(recipientId, medicationId, request, existing)) {
      throw new IdempotencyConflictException();
    }
    return response(existing);
  }

  private static boolean sameRequest(UUID recipientId, UUID medicationId,
      MedicationIntakeRequest request, MedicationIntakeEvent existing) {
    return Objects.equals(recipientId, existing.getRecipient().getId())
        && Objects.equals(medicationId, existing.getMedication().getId())
        && existing.getIntakeState() == request.intakeState()
        && Objects.equals(existing.getOccurredAt(), request.occurredAt())
        && existing.getVerificationState() == request.verificationState();
  }

  /**
   * Resolves a write that lost the unique-constraint race. Runs in a fresh read
   * transaction so it always observes the winner's committed row.
   */
  private MedicationIntakeResponse recoverCreate(UUID caregiverId, UUID recipientId, UUID medicationId,
      MedicationIntakeRequest request, String idempotencyKey, DataIntegrityViolationException failure) {
    return readTx.execute(status -> {
      if (idempotencyKey != null) {
        var existing = intakeEvents.findByCaregiver_IdAndIdempotencyKey(caregiverId, idempotencyKey);
        if (existing.isPresent()) {
          return replay(recipientId, medicationId, request, existing.get());
        }
      }
      throw failure;
    });
  }

  /**
   * Rejects timestamps outside the plausible range. The clock is the injected
   * UTC {@code SecurityBeans.systemClock()}, so the comparison is identical
   * wherever the server runs and does not depend on the host's local zone.
   */
  private void validateOccurredAt(Instant occurredAt) {
    if (occurredAt == null) {
      throw new InvalidIntakeTimeException("An intake time is required.");
    }
    Instant now = Instant.now(clock);
    if (occurredAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
      throw new InvalidIntakeTimeException("The intake time cannot be in the future.");
    }
    if (occurredAt.isBefore(now.minus(MAX_PAST_AGE))) {
      throw new InvalidIntakeTimeException("The intake time is too far in the past.");
    }
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

  public List<MedicationIntakeResponse> list(UUID recipientId, UUID medicationId) {
    return list(recipientId, medicationId, 0, DEFAULT_PAGE_SIZE);
  }

  /** Newest first, with a descending id tie-breaker so equal times stay stable. */
  @Transactional(readOnly = true)
  public List<MedicationIntakeResponse> list(UUID recipientId, UUID medicationId, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "page must be non-negative and size must be 1-" + MAX_PAGE_SIZE + ".");
    }
    UUID caregiverId = AuthContext.currentCaregiverId();
    ownedRecipient(recipientId, caregiverId);
    ownedMedication(medicationId, caregiverId, recipientId);
    var pageable = PageRequest.of(page, size,
        Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
    return intakeEvents
        .findAllByCaregiver_IdAndRecipient_IdAndMedication_Id(caregiverId, recipientId, medicationId, pageable)
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
