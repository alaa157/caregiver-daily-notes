package com.caregiver.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only acknowledgement queries that always include caregiver ownership. */
public interface SafetyAcknowledgementRepository extends Repository<SafetyAcknowledgement, UUID> {
  Optional<SafetyAcknowledgement> findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
      UUID caregiverId, UUID recipientId, SafetyRuleId ruleId, LocalDate periodEndInclusive, int periodDays);

  List<SafetyAcknowledgement> findAllByCaregiver_IdAndRecipient_IdOrderByPeriodEndInclusiveDescPeriodDaysAscRuleIdAsc(
      UUID caregiverId, UUID recipientId);
}
