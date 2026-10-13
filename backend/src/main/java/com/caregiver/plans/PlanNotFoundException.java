package com.caregiver.plans;

public class PlanNotFoundException extends RuntimeException {
  public PlanNotFoundException() {
    super("Plan not found.");
  }
}
