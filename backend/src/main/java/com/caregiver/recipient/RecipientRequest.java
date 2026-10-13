package com.caregiver.recipient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client-editable fields only. Ownership is deliberately not accepted here. */
@Schema(description = "Fields editable by the authenticated caregiver.")
public record RecipientRequest(
    @Schema(description = "Recipient's display name", example = "Samir Hassan")
        @NotBlank @Size(max = 120) String name,
    @Schema(description = "Relationship to the caregiver", example = "Father")
        @Size(max = 80) String relationship) {

  public RecipientRequest {
    if (name != null) {
      name = name.trim();
    }
    if (relationship != null) {
      relationship = relationship.trim();
      if (relationship.isEmpty()) {
        relationship = null;
      }
    }
  }
}
