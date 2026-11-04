package com.caregiver.recipient;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Safe recipient representation returned to its authenticated owner.")
public record RecipientResponse(
    UUID id,
    String name,
    String relationship,
    boolean active,
    RecipientLifecycleStatus lifecycleStatus,
    Instant archivedAt,
    UUID archivedBy,
    Instant restoredAt,
    UUID restoredBy,
    Instant createdAt,
    Instant updatedAt) {

  public static RecipientResponse from(CareRecipient recipient) {
    return new RecipientResponse(
        recipient.getId(), recipient.getName(), recipient.getRelationship(), recipient.isActive(),
        recipient.getLifecycleStatus(), recipient.getArchivedAt(), recipient.getArchivedBy(),
        recipient.getRestoredAt(), recipient.getRestoredBy(),
        recipient.getCreatedAt(), recipient.getUpdatedAt());
  }
}
