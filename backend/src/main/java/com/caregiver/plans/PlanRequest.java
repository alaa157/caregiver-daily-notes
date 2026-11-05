package com.caregiver.plans;

import jakarta.validation.constraints.Size;
import java.util.List;

public record PlanRequest(
    @Size(max = 20000) String content,
    @Size(max = 160) String sourceSummaryId,
    @Size(max = 40) List<@Size(max = 160) String> evidenceNoteIds,
    PlanDocument structuredContent) {
  public PlanRequest {
    evidenceNoteIds = evidenceNoteIds == null ? List.of() : List.copyOf(evidenceNoteIds);
  }

  public PlanRequest(String content, String sourceSummaryId, List<String> evidenceNoteIds) {
    this(content, sourceSummaryId, evidenceNoteIds, null);
  }
}
