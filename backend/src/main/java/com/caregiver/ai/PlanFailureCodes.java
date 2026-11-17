package com.caregiver.ai;

/**
 * Stable, non-sensitive reason codes for plan-generation failures. These are
 * safe to log: they identify which check rejected the output and never carry
 * plan text, note text, medication values or other health data.
 */
public final class PlanFailureCodes {

  public static final String INVALID_MODEL_OUTPUT = "invalid_model_output";
  public static final String PROTECTED_PLAN = "protected_plan";
  /**
   * Reserved for genuine server-side lock rejections on server-owned values.
   * Since issue #751 a model-supplied medication echo is never validated, so
   * generation cannot fail with this code because of an echo difference.
   */
  public static final String MEDICATION_LOCK = "medication_lock";
  public static final String TEXT_SAFETY = "text_safety";

  private PlanFailureCodes() {
  }
}
