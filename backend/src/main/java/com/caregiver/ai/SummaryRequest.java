package com.caregiver.ai;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * A request for an on-demand summary over a fixed window. Only 7, 14, and
 * 30-day periods are supported.
 */
public record SummaryRequest(String recipientId, LocalDate periodEndInclusive, int periodDays) {

  /** The only supported summary windows in days. */
  public static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 14, 30);

  public SummaryRequest {
    if (recipientId == null || recipientId.isBlank()) {
      throw new IllegalArgumentException("recipientId must be non-blank");
    }
    Objects.requireNonNull(periodEndInclusive, "periodEndInclusive");
    if (!ALLOWED_PERIODS.contains(periodDays)) {
      throw new IllegalArgumentException("periodDays must be one of " + ALLOWED_PERIODS);
    }
  }
}
