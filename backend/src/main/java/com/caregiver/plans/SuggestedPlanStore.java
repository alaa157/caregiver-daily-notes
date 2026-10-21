package com.caregiver.plans;

/**
 * Persistence port for validated suggested plans. Mohamed Hemdan implements the
 * real boundary against the plan state machine (issues #9 and #11); until then
 * tests and development use the in-memory fake. Only fully-validated plans
 * reach this port — validation lives in the suggestion service, never here.
 */
public interface SuggestedPlanStore {

  StoredPlanVersion save(String recipientId, SuggestedPlan plan);
}
