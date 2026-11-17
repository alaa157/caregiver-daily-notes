package com.caregiver.ai;

import java.util.List;

/**
 * Outcome of checking each model observation against its cited notes.
 *
 * <p>Observations that fail the (unchanged) grounding check are rejected
 * individually; the rest survive. Only a structurally unusable response still
 * fails the whole request. {@link #reason()} is the stable response code used
 * by clients to explain how many observations were dropped and why.</p>
 */
public record EvidenceValidation(
    List<SummaryObservation> accepted,
    List<Rejected> rejected,
    String reason) {

  /** Stable machine-readable reason for a dropped observation. */
  public static final String REASON_UNVERIFIED_QUOTE = "UNVERIFIED_QUOTE";

  public EvidenceValidation {
    accepted = List.copyOf(accepted);
    rejected = List.copyOf(rejected);
  }

  /** One dropped observation and the human-readable detail behind it. */
  public record Rejected(int index, String detail) {
  }

  public static EvidenceValidation allAccepted(List<SummaryObservation> observations) {
    return new EvidenceValidation(observations, List.of(), REASON_UNVERIFIED_QUOTE);
  }
}
