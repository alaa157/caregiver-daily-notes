package com.caregiver.ai;

import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records and reads caregiver-owned safety review metadata only. */
@Service
public class SafetyAcknowledgementService {
  private final CareRecipientRepository recipients;
  private final SafetyAcknowledgementRepository acknowledgements;

  public SafetyAcknowledgementService(CareRecipientRepository recipients,
      SafetyAcknowledgementRepository acknowledgements) {
    this.recipients = recipients;
    this.acknowledgements = acknowledgements;
  }

  @Transactional
  public SafetyAcknowledgementResponse acknowledge(UUID recipientId, SafetyAcknowledgementRequest request) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    ownedRecipient(recipientId, caregiverId);
    // Serialize the composite-key lookup and insert for this recipient.
    CareRecipient recipient = recipients.findOwnedForUpdate(recipientId, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
    return acknowledgements.findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
        caregiverId, recipientId, request.ruleId(), request.periodEndInclusive(), request.periodDays())
        .map(this::response)
        .orElseGet(() -> response(acknowledgements.saveAndFlush(new SafetyAcknowledgement(
            recipient.getCaregiver(), recipient, request.ruleId(), request.periodEndInclusive(), request.periodDays()))));
  }

  @Transactional(readOnly = true)
  public List<SafetyAcknowledgementResponse> list(UUID recipientId, LocalDate periodEndInclusive, int periodDays) {
    if (periodEndInclusive == null || !SummaryRequest.ALLOWED_PERIODS.contains(periodDays)) {
      throw new IllegalArgumentException("Unsupported safety acknowledgement period.");
    }
    UUID caregiverId = AuthContext.currentCaregiverId();
    ownedRecipient(recipientId, caregiverId);
    return acknowledgements
        .findAllByCaregiver_IdAndRecipient_IdAndPeriodEndInclusiveAndPeriodDaysOrderByRuleIdAsc(
            caregiverId, recipientId, periodEndInclusive, periodDays)
        .stream()
        .map(this::response)
        .toList();
  }

  private CareRecipient ownedRecipient(UUID recipientId, UUID caregiverId) {
    return recipients.findByIdAndCaregiver_Id(recipientId, caregiverId)
        .orElseThrow(RecipientNotFoundException::new);
  }

  private SafetyAcknowledgementResponse response(SafetyAcknowledgement acknowledgement) {
    return new SafetyAcknowledgementResponse(
        acknowledgement.getId(),
        acknowledgement.getRecipient().getId(),
        acknowledgement.getRuleId(),
        acknowledgement.getPeriodEndInclusive(),
        acknowledgement.getPeriodDays(),
        acknowledgement.getCaregiver().getId(),
        acknowledgement.getAcknowledgedAt());
  }
}
