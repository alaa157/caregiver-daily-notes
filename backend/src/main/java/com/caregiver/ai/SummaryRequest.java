package com.caregiver.ai;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * A request for an on-demand summary over a fixed window. Only 7, 14, and
 * 30-day periods are supported.
 */
public record SummaryRequest(String recipientId, LocalDate periodEndInclusive, int periodDays, String language) {

  /** The only supported summary windows in days. */
  public static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 14, 30);

  public SummaryRequest(String recipientId, LocalDate periodEndInclusive, int periodDays) {
    this(recipientId, periodEndInclusive, periodDays, null);
  }

  public SummaryRequest {
    if (recipientId == null || recipientId.isBlank()) {
      throw new IllegalArgumentException("recipientId must be non-blank");
    }
    Objects.requireNonNull(periodEndInclusive, "periodEndInclusive");
    if (!ALLOWED_PERIODS.contains(periodDays)) {
      throw new IllegalArgumentException("periodDays must be one of " + ALLOWED_PERIODS);
    }
    language = AiOutputLanguage.normalize(language);
  }
}
