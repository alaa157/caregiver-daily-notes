package com.caregiver.plans;

public class IllegalPlanTransitionException extends RuntimeException {
  public IllegalPlanTransitionException(String message) {
    super(message);
  }
}
