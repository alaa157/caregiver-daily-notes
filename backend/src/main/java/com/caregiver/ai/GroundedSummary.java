package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A validated model summary: grounded observations plus explicit
 * uncertainties. {@code aiUnavailable} marks provider-fallback results,
 * which carry no model content.
 */
public record GroundedSummary(
    List<SummaryObservation> observations,
    List<SummaryUncertainty> uncertainties,
    boolean aiUnavailable,
    @Schema(
        description = "Reason the AI summary is unavailable.",
        allowableValues = {
            "INVALID_KEY", "INVALID_MODEL", "RATE_LIMIT", "TIMEOUT",
            "PROVIDER_UNAVAILABLE", "INVALID_OUTPUT"
        })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String unavailableReason,
    /** Observations dropped because their quote could not be verified. */
    int droppedObservationCount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String droppedReason,
    /** Observations or uncertainties dropped because their text was unsafe. */
    int droppedContentCount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String droppedContentReason) {

  public GroundedSummary {
    observations = observations == null ? List.of() : List.copyOf(observations);
    uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
  }

  public GroundedSummary(
      List<SummaryObservation> observations, List<SummaryUncertainty> uncertainties, boolean aiUnavailable) {
    this(observations, uncertainties, aiUnavailable, null, 0, null, 0, null);
  }

  public GroundedSummary(
      List<SummaryObservation> observations, List<SummaryUncertainty> uncertainties, boolean aiUnavailable,
      String unavailableReason) {
    this(observations, uncertainties, aiUnavailable, unavailableReason, 0, null, 0, null);
  }

  public GroundedSummary(
      List<SummaryObservation> observations, List<SummaryUncertainty> uncertainties, boolean aiUnavailable,
      String unavailableReason, int droppedObservationCount, String droppedReason) {
    this(observations, uncertainties, aiUnavailable, unavailableReason, droppedObservationCount, droppedReason,
        0, null);
  }
}
