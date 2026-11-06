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

  Optional<MedicationIntakeEvent> findByIdAndCaregiver_IdAndRecipient_Id(
      UUID eventId, UUID caregiverId, UUID recipientId);

  List<MedicationIntakeEvent> findAllByCaregiver_IdAndRecipient_IdOrderByOccurredAtDescIdAsc(
      UUID caregiverId, UUID recipientId);

  List<MedicationIntakeEvent> findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtDescIdAsc(
      UUID caregiverId, UUID recipientId, UUID medicationId);
}
