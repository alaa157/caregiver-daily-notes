package com.caregiver.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Public acknowledgement representation without persistence entity details. */
public record SafetyAcknowledgementResponse(
    UUID id,
    UUID recipientId,
    SafetyRuleId ruleId,
    LocalDate periodEndInclusive,
    int periodDays,
    UUID actorCaregiverId,
    Instant acknowledgedAt) {
}
