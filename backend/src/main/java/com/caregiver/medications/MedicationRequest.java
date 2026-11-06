package com.caregiver.medications;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Client-editable medication details; ownership is derived from authentication and the URL. */
@Schema(description = "Caregiver-entered medication details.")
public record MedicationRequest(
    @Schema(description = "Caregiver-entered medication name", example = "Vitamin D")
    @NotBlank @Size(max = 160) String displayName,
    @Schema(description = "Caregiver-entered dose description", example = "1000 IU")
    @NotNull @Size(max = 500) String doseText,
    @Schema(description = "Caregiver-entered schedule description", example = "Once daily after breakfast")
    @NotNull @Size(max = 500) String scheduleText) {
}
