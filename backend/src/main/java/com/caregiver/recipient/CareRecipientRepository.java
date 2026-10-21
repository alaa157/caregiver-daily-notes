package com.caregiver.recipient;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All read operations used by the API are explicitly scoped to the owner. */
public interface CareRecipientRepository extends JpaRepository<CareRecipient, UUID> {
  List<CareRecipient> findAllByCaregiver_IdOrderByCreatedAtDesc(UUID caregiverId);
  Optional<CareRecipient> findByIdAndCaregiver_Id(UUID id, UUID caregiverId);

  /** Serializes version allocation and plan acceptance for a recipient. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from CareRecipient r where r.id = :id and r.caregiver.id = :caregiverId")
  Optional<CareRecipient> findOwnedForUpdate(@Param("id") UUID id, @Param("caregiverId") UUID caregiverId);
}
