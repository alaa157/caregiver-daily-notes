package com.caregiver.preview;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateNoteRequest(
    @NotNull UUID recipientId,
    @Size(max = 32) String mood,
    @Size(max = 32) String appetite,
    @Size(max = 32) String sleep,
    @Size(max = 32) String mobility,
    @Size(max = 32) String medicationTaken,
    @Min(0) @Max(10) int pain,
    boolean fall,
    @NotBlank String text) {
}
