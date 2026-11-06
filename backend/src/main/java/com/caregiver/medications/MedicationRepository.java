package com.caregiver.medications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Medication persistence operations that always scope records to caregiver and recipient. */
public interface MedicationRepository extends Repository<MedicationRecord, UUID> {
  MedicationRecord save(MedicationRecord medicationRecord);

  Optional<MedicationRecord> findByIdAndCaregiver_IdAndRecipient_Id(
      UUID medicationId, UUID caregiverId, UUID recipientId);

  List<MedicationRecord> findAllByCaregiver_IdAndRecipient_IdOrderByUpdatedAtDescIdAsc(
      UUID caregiverId, UUID recipientId);
}
