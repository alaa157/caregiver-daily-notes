package com.caregiver.ai;

/**
 * Explicitly marked unclear or conflicting information. The model must
 * produce these instead of guessing when evidence is ambiguous.
 */
public record SummaryUncertainty(String topic, String detail) {

  public SummaryUncertainty {
    if (topic == null || detail == null) {
      throw new IllegalArgumentException("topic and detail must be non-null");
    }
  }
}
