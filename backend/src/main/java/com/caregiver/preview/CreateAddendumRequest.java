package com.caregiver.preview;

import jakarta.validation.constraints.NotBlank;

public record CreateAddendumRequest(@NotBlank String text) {
}
