package com.caregiver.preview;

import java.util.List;

public record PlanVersionDto(
    int version, String status, List<String> items, String createdAt, String reason) {

  static PlanVersionDto of(PlanVersion version, String reason) {
    return new PlanVersionDto(
        version.getVersion(),
        version.getStatus(),
        List.copyOf(version.getItems()),
        version.getCreatedAt().toString(),
        reason);
  }
}
