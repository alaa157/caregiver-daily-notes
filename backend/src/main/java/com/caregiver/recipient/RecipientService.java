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

/** Enforces recipient ownership at the service boundary for every operation. */
@Service
public class RecipientService {
  private final CareRecipientRepository recipients;
  private final CaregiverRepository caregivers;
  private final AuditService audit;

  public RecipientService(CareRecipientRepository recipients, CaregiverRepository caregivers,
      AuditService audit) {
    this.recipients = recipients;
    this.caregivers = caregivers;
    this.audit = audit;
  }

  @Transactional
  public RecipientResponse create(RecipientRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    Caregiver caregiver = caregivers.findById(caregiverId)
        .orElseThrow(() -> new InvalidTokenException("Authentication required."));
    CareRecipient recipient = new CareRecipient(caregiver, request.name(), request.relationship());
    recipient = recipients.save(recipient);
    audit.record("RECIPIENT_CREATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
    return RecipientResponse.from(recipient);
  }

  @Transactional(readOnly = true)
  public List<RecipientResponse> listMine() {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return recipients.findAllByCaregiver_IdOrderByCreatedAtDesc(caregiverId).stream()
        .map(RecipientResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public RecipientResponse getMine(UUID id) {
    return RecipientResponse.from(findOwned(id));
  }

  @Transactional
  public RecipientResponse update(UUID id, RecipientRequest request) {
    CareRecipient recipient = findOwned(id);
    if (!recipient.isActive()) {
      throw new InactiveRecipientException();
    }
    recipient.update(request.name(), request.relationship());
    recipient = recipients.save(recipient);
    audit.record("RECIPIENT_UPDATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
    return RecipientResponse.from(recipient);
  }

  @Transactional
  public RecipientResponse deactivate(UUID id) {
    CareRecipient recipient = findOwned(id);
    recipient.deactivate();
    recipient = recipients.save(recipient);
    audit.record("RECIPIENT_DEACTIVATED", "CARE_RECIPIENT", recipient.getId().toString(), null);
    return RecipientResponse.from(recipient);
  }

  private CareRecipient findOwned(UUID id) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return recipients.findByIdAndCaregiver_Id(id, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }
}
