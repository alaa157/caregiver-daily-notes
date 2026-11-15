package com.caregiver.recipient;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Enforces recipient ownership and lifecycle transitions at the service boundary. */
@Service
public class RecipientService {
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final CareRecipientRepository recipients;
  private final CaregiverRepository caregivers;
  private final AuditService audit;
  private final RecipientCreateRequestRepository createRequests;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public RecipientService(CareRecipientRepository recipients, CaregiverRepository caregivers,
      AuditService audit, RecipientCreateRequestRepository createRequests,
      PlatformTransactionManager transactionManager) {
    this.recipients = recipients;
    this.caregivers = caregivers;
    this.audit = audit;
    this.createRequests = createRequests;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  /**
   * Creates a recipient, optionally idempotently. An {@code Idempotency-Key}
   * header makes retries safe: the same key plus the same payload replays the
   * original recipient with {@code 201} and writes no second audit event,
   * while the same key with a different payload is rejected with
   * {@link IdempotencyConflictException}. The request row is written in the
   * same transaction as the recipient, and the database unique constraint on
   * {@code (caregiver_id, idempotency_key)} serializes concurrent duplicates.
   */
  public RecipientResponse create(RecipientRequest request, String key) {
    UUID owner = AuthContext.currentCaregiverId();
    String idempotencyKey = normalizeKey(key);
    String digest = idempotencyKey == null ? null : digest(request);
    if (idempotencyKey != null) {
      RecipientResponse replay = readTx.execute(status -> createRequests
          .findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey)
          .map(existing -> replayResponse(owner, existing, digest))
          .orElse(null));
      if (replay != null) {
        return replay;
      }
    }
    try {
      return writeTx.execute(status -> {
        if (idempotencyKey != null) {
          var existing = createRequests.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
          if (existing.isPresent()) {
            return replayResponse(owner, existing.get(), digest);
          }
        }
        Caregiver caregiver = caregivers.findById(owner)
            .orElseThrow(() -> new InvalidTokenException("Authentication required."));
        CareRecipient recipient = recipients.saveAndFlush(
            new CareRecipient(caregiver, request.name(), request.relationship()));
        if (idempotencyKey != null) {
          createRequests.saveAndFlush(
              new RecipientCreateRequest(caregiver, recipient, idempotencyKey, digest));
        }
        audit.record("RECIPIENT_CREATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
        return RecipientResponse.from(recipient);
      });
    } catch (DataIntegrityViolationException e) {
      return recoverCreate(owner, idempotencyKey, digest, e);
    }
  }

  /**
   * Resolves a create that lost the unique-constraint race. Runs in a fresh
   * read transaction so it always observes the winner's committed row.
   */
  private RecipientResponse recoverCreate(UUID owner, String idempotencyKey, String digest,
      DataIntegrityViolationException failure) {
    return readTx.execute(status -> {
      if (idempotencyKey != null) {
        var existing = createRequests.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
        if (existing.isPresent()) {
          return replayResponse(owner, existing.get(), digest);
        }
      }
      throw failure;
    });
  }

  private RecipientResponse replayResponse(UUID owner, RecipientCreateRequest existing, String digest) {
    if (!Objects.equals(existing.getRequestDigest(), digest)) {
      throw new IdempotencyConflictException();
    }
    CareRecipient recipient = recipients
        .findByIdAndCaregiver_Id(existing.getRecipient().getId(), owner)
        .orElseThrow(RecipientNotFoundException::new);
    return RecipientResponse.from(recipient);
  }

  private String digest(RecipientRequest request) {
    try {
      String canonical = request.name() + "\n"
          + (request.relationship() == null ? "" : request.relationship());
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is unavailable.", e);
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

  @Transactional(readOnly = true)
  public List<RecipientResponse> listMine(RecipientLifecycleStatus status) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    var owned = status == null
        ? recipients.findAllByCaregiver_IdOrderByCreatedAtDesc(caregiverId)
        : recipients.findAllByCaregiver_IdAndLifecycleStatusOrderByCreatedAtDesc(caregiverId, status);
    return owned.stream().map(RecipientResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public RecipientResponse getMine(UUID id) {
    return RecipientResponse.from(findOwned(id));
  }

  @Transactional
  public RecipientResponse update(UUID id, RecipientRequest request) {
    CareRecipient recipient = findOwnedForUpdate(id);
    recipient.update(request.name(), request.relationship());
    recipient = recipients.saveAndFlush(recipient);
    audit.record("RECIPIENT_UPDATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
    return RecipientResponse.from(recipient);
  }

  @Transactional
  public RecipientResponse archive(UUID id) {
    CareRecipient recipient = findOwnedForUpdate(id);
    if (recipient.getLifecycleStatus() == RecipientLifecycleStatus.LEGACY_INACTIVE_REVIEW) {
      throw new InactiveRecipientException("This legacy inactive recipient requires review; it was not assumed to be archived.");
    }
    if (recipient.getLifecycleStatus() == RecipientLifecycleStatus.ACTIVE) {
      UUID actor = AuthContext.currentCaregiverId();
      recipient.archive(actor);
      recipient = recipients.saveAndFlush(recipient);
      audit.record("RECIPIENT_ARCHIVED", "CARE_RECIPIENT", recipient.getId().toString(), "lifecycleStatus=ARCHIVED");
    }
    return RecipientResponse.from(recipient);
  }

  @Transactional
  public RecipientResponse restore(UUID id) {
    CareRecipient recipient = findOwnedForUpdate(id);
    if (recipient.getLifecycleStatus() == RecipientLifecycleStatus.LEGACY_INACTIVE_REVIEW) {
      throw new InactiveRecipientException("This legacy inactive recipient requires review; it cannot be restored automatically.");
    }
    if (recipient.getLifecycleStatus() == RecipientLifecycleStatus.ARCHIVED) {
      UUID actor = AuthContext.currentCaregiverId();
      recipient.restore(actor);
      recipient = recipients.saveAndFlush(recipient);
      audit.record("RECIPIENT_RESTORED", "CARE_RECIPIENT", recipient.getId().toString(), "lifecycleStatus=ACTIVE");
    }
    return RecipientResponse.from(recipient);
  }

  @Transactional
  public RecipientResponse resolveReview(UUID id, String resolution) {
    CareRecipient recipient = findOwnedForUpdate(id);
    UUID actor = AuthContext.currentCaregiverId();
    if ("ARCHIVED".equals(resolution)) {
      recipient.resolveReviewAsArchived(actor);
    } else if ("ACTIVE".equals(resolution)) {
      recipient.resolveReviewAsActive(actor);
    } else {
      throw new IllegalArgumentException("resolution must be ARCHIVED or ACTIVE.");
    }
    recipient = recipients.saveAndFlush(recipient);
    audit.record("RECIPIENT_REVIEW_RESOLVED", "CARE_RECIPIENT", recipient.getId().toString(),
        "resolution=" + resolution);
    return RecipientResponse.from(recipient);
  }

  /** Backward-compatible alias for older clients; now means reversible archive. */
  @Transactional
  public RecipientResponse deactivate(UUID id) {
    return archive(id);
  }

  private CareRecipient findOwned(UUID id) {
    return recipients.findByIdAndCaregiver_Id(id, AuthContext.currentCaregiverId())
        .orElseThrow(RecipientNotFoundException::new);
  }

  private CareRecipient findOwnedForUpdate(UUID id) {
    return recipients.findOwnedForUpdate(id, AuthContext.currentCaregiverId())
        .orElseThrow(RecipientNotFoundException::new);
  }
}
