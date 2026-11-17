package com.caregiver.ai;

import com.caregiver.plans.PlanTextSafety;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the same content standard as plan text to generated summary text.
 * Screening is per item: an observation or uncertainty containing an obvious
 * diagnosis or medical instruction is dropped, while the rest of the summary
 * survives. Both English and Arabic are covered by {@link PlanTextSafety}.
 *
 * <p>This does not touch evidence/quote validation or the prompt.</p>
 */
public final class SummaryTextSafety {

  /** Stable reason code returned to clients when unsafe text was removed. */
  public static final String REASON_UNSAFE_CONTENT = "UNSAFE_CONTENT";

  public record Screened(
      List<SummaryObservation> observations,
      List<SummaryUncertainty> uncertainties,
      int removedCount) {
  }

  private SummaryTextSafety() {
  }

  public static Screened screen(List<SummaryObservation> observations,
      List<SummaryUncertainty> uncertainties) {
    List<SummaryObservation> safeObservations = new ArrayList<>();
    int removed = 0;
    for (SummaryObservation observation : observations == null ? List.<SummaryObservation>of() : observations) {
      if (observation == null || PlanTextSafety.isUnsafe(observation.text())) {
        removed++;
      } else {
        safeObservations.add(observation);
      }
    }
    List<SummaryUncertainty> safeUncertainties = new ArrayList<>();
    for (SummaryUncertainty uncertainty : uncertainties == null ? List.<SummaryUncertainty>of() : uncertainties) {
      if (uncertainty == null || PlanTextSafety.isUnsafe(uncertainty.topic())
          || PlanTextSafety.isUnsafe(uncertainty.detail())) {
        removed++;
      } else {
        safeUncertainties.add(uncertainty);
      }
    }
    return new Screened(safeObservations, safeUncertainties, removed);
  }
}
