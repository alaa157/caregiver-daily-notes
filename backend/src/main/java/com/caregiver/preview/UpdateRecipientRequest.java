package com.caregiver.preview;

import jakarta.validation.constraints.NotNull;

public record UpdateRecipientRequest(@NotNull Boolean active) {
}
