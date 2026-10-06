package com.caregiver.ai;

import java.util.List;

/**
 * A validated model summary: grounded observations plus explicit
 * uncertainties. {@code aiUnavailable} marks provider-fallback results,
 * which carry no model content.
 */
public record GroundedSummary(
    List<SummaryObservation> observations, List<SummaryUncertainty> uncertainties, boolean aiUnavailable) {

  public GroundedSummary {
    observations = observations == null ? List.of() : List.copyOf(observations);
    uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
  }
}
