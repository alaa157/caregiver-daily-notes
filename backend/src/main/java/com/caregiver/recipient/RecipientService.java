package com.caregiver.recipient;

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

  public RecipientService(CareRecipientRepository recipients, CaregiverRepository caregivers) {
    this.recipients = recipients;
    this.caregivers = caregivers;
  }

  @Transactional
  public RecipientResponse create(RecipientRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    Caregiver caregiver = caregivers.findById(caregiverId)
        .orElseThrow(() -> new InvalidTokenException("Authentication required."));
    CareRecipient recipient =
        new CareRecipient(caregiver, request.name(), request.relationship());
    return RecipientResponse.from(recipients.save(recipient));
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
    return RecipientResponse.from(recipients.save(recipient));
  }

  @Transactional
  public RecipientResponse deactivate(UUID id) {
    CareRecipient recipient = findOwned(id);
    recipient.deactivate();
    // Soft state change only. No dependent history is deleted.
    return RecipientResponse.from(recipients.save(recipient));
  }

  private CareRecipient findOwned(UUID id) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return recipients.findByIdAndCaregiver_Id(id, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }
}
