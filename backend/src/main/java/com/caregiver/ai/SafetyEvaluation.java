package com.caregiver.ai;

import java.util.List;

/**
 * Deterministic evaluation result. The doctor banner is derived by code
 * (any {@code HIGH} signal), never by the model.
 */
public record SafetyEvaluation(List<SafetySignal> signals, boolean needsDoctorBanner) {

  public SafetyEvaluation {
    signals = signals == null ? List.of() : List.copyOf(signals);
  }
}
