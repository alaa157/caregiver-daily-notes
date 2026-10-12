package com.caregiver.recipient;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Safe recipient representation; never exposes caregiver credentials or ownership internals. */
@Schema(description = "Care recipient returned by the API.")
public record RecipientResponse(
    UUID id,
    String name,
    String relationship,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static RecipientResponse from(CareRecipient recipient) {
    return new RecipientResponse(
        recipient.getId(),
        recipient.getName(),
        recipient.getRelationship(),
        recipient.isActive(),
        recipient.getCreatedAt(),
        recipient.getUpdatedAt());
  }
}
