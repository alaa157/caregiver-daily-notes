package com.caregiver.ai;

import java.util.Objects;

/**
 * Result of one provider call. Fallback results carry empty text and an
 * {@code AI_UNAVAILABLE}-prefixed error; they never carry model content.
 */
public record LlmResult(LlmStatus status, String text, String error) {

  public LlmResult {
    Objects.requireNonNull(status, "status");
    if (text == null) {
      text = "";
    }
  }
}
