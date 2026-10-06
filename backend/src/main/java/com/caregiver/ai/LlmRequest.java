package com.caregiver.ai;

/**
 * A single provider call. The system prompt carries instructions only —
 * caregiver notes travel exclusively in {@code dataBlock}, wrapped in
 * delimiters by the client, never in the system prompt.
 */
public record LlmRequest(String systemPrompt, String dataBlock) {

  public LlmRequest {
    if (systemPrompt == null || systemPrompt.isBlank()) {
      throw new IllegalArgumentException("systemPrompt must be non-blank");
    }
    if (dataBlock == null) {
      throw new IllegalArgumentException("dataBlock must be non-null (may be empty)");
    }
  }
}
