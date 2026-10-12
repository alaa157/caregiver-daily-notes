package com.caregiver.recipient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All read operations used by the API are explicitly scoped to the owner. */
public interface CareRecipientRepository extends JpaRepository<CareRecipient, UUID> {

  List<CareRecipient> findAllByCaregiver_IdOrderByCreatedAtDesc(UUID caregiverId);

  Optional<CareRecipient> findByIdAndCaregiver_Id(UUID id, UUID caregiverId);
}
