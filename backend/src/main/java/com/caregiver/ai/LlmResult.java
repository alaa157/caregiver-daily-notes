package com.caregiver.ai;

import java.util.Objects;

/**
 * Result of one provider call. Fallback results carry empty text, an
 * {@code AI_UNAVAILABLE}-prefixed error, and their failure reason; they never
 * carry model content.
 */
public record LlmResult(LlmStatus status, String text, String error, FailureReason failureReason) {

  public LlmResult(LlmStatus status, String text, String error) {
    this(status, text, error, null);
  }

  public LlmResult {
    Objects.requireNonNull(status, "status");
    if (text == null) {
      text = "";
    }
  }
}
