package com.caregiver.ai;

import com.caregiver.plans.MedicationEntry;

import java.util.List;

/**
 * One synthetic golden evaluation case. All data is invented for testing —
 * {@code synthetic} must read {@code "synthetic-v1"} and no case may contain
 * real patient information.
 */
public record GoldenEvalCase(
    String id,
    String synthetic,
    List<String> categories,
    List<TestSignal> signals,
    String modelText,
    String modelStatus,
    List<QuoteRef> quotes,
    List<String> expectedFlags,
    boolean expectValid,
    boolean expectGrounded,
    boolean expectMedsSafe,
    List<MedicationEntry> currentMedications,
    List<MedicationEntry> proposedMedications) {

  public GoldenEvalCase(
      String id, String synthetic, List<String> categories, List<TestSignal> signals,
      String modelText, String modelStatus, List<QuoteRef> quotes, List<String> expectedFlags,
      boolean expectValid, boolean expectGrounded, boolean expectMedsSafe) {
    this(id, synthetic, categories, signals, modelText, modelStatus, quotes, expectedFlags,
        expectValid, expectGrounded, expectMedsSafe, List.of(), List.of());
  }

  public record TestSignal(
      boolean fall,
      Integer pain,
      Boolean missed,
      boolean unverified,
      Boolean appetite,
      String text,
      int dayOffset) {
  }

  public record QuoteRef(int note, String quote) {
  }
}
