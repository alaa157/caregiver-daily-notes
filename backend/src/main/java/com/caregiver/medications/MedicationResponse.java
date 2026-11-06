package com.caregiver.medications;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Safe medication representation returned to the caregiver who owns its recipient. */
@Schema(description = "Caregiver-maintained medication record.")
public record MedicationResponse(
    UUID id,
    UUID recipientId,
    String displayName,
    String doseText,
    String scheduleText,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static MedicationResponse from(MedicationRecord record) {
    return new MedicationResponse(record.getId(), record.getRecipient().getId(),
        record.getDisplayName(), record.getDoseText(), record.getScheduleText(),
        record.isActive(), record.getCreatedAt(), record.getUpdatedAt());
  }
}
