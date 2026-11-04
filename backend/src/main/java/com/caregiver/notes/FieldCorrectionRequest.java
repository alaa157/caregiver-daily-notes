package com.caregiver.notes;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A missing value is meaningful only for non-SET operations. */
public record FieldCorrectionRequest(@NotBlank String field, @NotNull CorrectionOperation operation, JsonNode value) { }
