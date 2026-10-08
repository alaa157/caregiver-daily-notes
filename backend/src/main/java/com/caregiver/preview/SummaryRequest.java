package com.caregiver.preview;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SummaryRequest(@NotNull UUID recipientId, @Min(1) int periodDays) {
}
