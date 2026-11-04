package com.caregiver.ai;

/**
 * Provider boundary. Application code depends on this interface, never on a
 * provider implementation. Implementations return {@code FALLBACK} for
 * ordinary provider failures and propagate cancellation when interrupted.
 */
public interface LlmClient {

  LlmResult complete(LlmRequest request);
}
