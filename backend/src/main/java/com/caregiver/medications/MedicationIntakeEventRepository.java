package com.caregiver.medications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Medication intake event persistence scoped to caregiver, recipient, and medication. */
public interface MedicationIntakeEventRepository extends Repository<MedicationIntakeEvent, UUID> {
  MedicationIntakeEvent save(MedicationIntakeEvent intakeEvent);

  Optional<MedicationIntakeEvent> findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
      UUID eventId, UUID caregiverId, UUID recipientId, UUID medicationId);

  List<MedicationIntakeEvent> findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
      UUID caregiverId, UUID recipientId, UUID medicationId);
}
