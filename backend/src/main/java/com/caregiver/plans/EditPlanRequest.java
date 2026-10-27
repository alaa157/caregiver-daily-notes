package com.caregiver.plans;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Original content, medications, actions and provenance remain server-owned. */
public record EditPlanRequest(@Min(1) int expectedVersion, @NotNull @Size(max = 20) List<@Valid PlanChangeEdit> changes) {
  public EditPlanRequest { if (changes != null) changes = List.copyOf(changes); }
  @JsonAnySetter public void rejectUnknown(String field, Object value) { throw new ProtectedPlanException(); }
}
