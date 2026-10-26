package com.caregiver.plans;

public class ProtectedPlanException extends RuntimeException {
  public ProtectedPlanException() { super("This plan cannot use that protected editing operation."); }
}
