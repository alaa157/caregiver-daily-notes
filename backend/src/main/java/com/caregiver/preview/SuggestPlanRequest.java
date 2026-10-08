package com.caregiver.preview;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SuggestPlanRequest(@NotNull UUID recipientId, List<String> items) {
}
