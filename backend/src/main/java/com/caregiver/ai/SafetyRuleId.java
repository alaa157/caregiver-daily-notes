package com.caregiver.ai;

/**
 * Stable rule identifiers for safety signals. {@link #getId()} returns the
 * wire-stable string id — never rename values without a migration.
 */
public enum SafetyRuleId {
  FALL_DETECTED("FALL_DETECTED"),
  HIGH_PAIN("HIGH_PAIN"),
  REPEATED_MISSED_MED("REPEATED_MISSED_MED"),
  POOR_APPETITE_DAYS("POOR_APPETITE_DAYS"),

  /**
   * Raised by the LLM when its summary marks the window urgent. Lives on
   * after the deterministic merge: the model may ADD urgency (this rule),
   * never remove or downgrade a deterministic one. */
  MODEL_URGENT("MODEL_URGENT");

  private final String id;

  SafetyRuleId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
