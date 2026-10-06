package com.caregiver.ai;

/**
 * Provider boundary. Application code depends on this interface, never on a
 * provider implementation. Implementations must never throw from
 * {@code complete} — failures become {@code FALLBACK} results.
 */
public interface LlmClient {

  LlmResult complete(LlmRequest request);
}
