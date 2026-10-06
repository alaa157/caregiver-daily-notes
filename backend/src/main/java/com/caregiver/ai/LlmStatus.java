package com.caregiver.ai;

/**
 * Outcome of one provider call: clean success, success after the single
 * JSON-repair retry, or deterministic fallback when the provider failed.
 */
public enum LlmStatus {
  OK,
  REPAIRED,
  FALLBACK
}
