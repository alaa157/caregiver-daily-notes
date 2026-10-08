package com.caregiver.preview;

import java.util.List;

public record PlanDto(String id, String recipientId, List<PlanVersionDto> versions) {
}
