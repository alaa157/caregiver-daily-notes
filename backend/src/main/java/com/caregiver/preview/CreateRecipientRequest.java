package com.caregiver.preview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRecipientRequest(@NotBlank @Size(max = 320) String name) {
}
