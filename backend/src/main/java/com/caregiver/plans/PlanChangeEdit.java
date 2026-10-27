package com.caregiver.plans;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.Map;

/** Only existing non-medication parameter values may change. */
public record PlanChangeEdit(int index, Map<String, String> params) {
  public PlanChangeEdit { if (params == null) throw new ProtectedPlanException(); params = Map.copyOf(params); }
  @JsonAnySetter public void rejectUnknown(String field, Object value) { throw new ProtectedPlanException(); }
}
