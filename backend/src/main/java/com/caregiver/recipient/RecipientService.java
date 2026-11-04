package com.caregiver.recipient;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Enforces recipient ownership and lifecycle transitions at the service boundary. */
@Service
public class RecipientService {
  private final CareRecipientRepository recipients;
  private final CaregiverRepository caregivers;
  private final AuditService audit;

  public RecipientService(CareRecipientRepository recipients, CaregiverRepository caregivers, AuditService audit) {
    this.recipients = recipients;
    this.caregivers = caregivers;
    this.audit = audit;
  }

  @Transactional
  public RecipientResponse create(RecipientRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    Caregiver caregiver = caregivers.findById(caregiverId)
        .orElseThrow(() -> new InvalidTokenException("Authentication required."));
    CareRecipient recipient = recipients.saveAndFlush(new CareRecipient(caregiver, request.name(), request.relationship()));
    audit.record("RECIPIENT_CREATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
    return RecipientResponse.from(recipient);
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
