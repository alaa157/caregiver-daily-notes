package com.caregiver.plans;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlanRequest(
    @NotBlank @Size(max = 20000) String content,
    @Size(max = 160) String sourceSummaryId,
    @Size(max = 40) List<@Size(max = 160) String> evidenceNoteIds) {
  public PlanRequest {
    evidenceNoteIds = evidenceNoteIds == null ? List.of() : List.copyOf(evidenceNoteIds);
  }
}
