package com.caregiver.recipient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped lookups for idempotent recipient create requests. */
public interface RecipientCreateRequestRepository extends JpaRepository<RecipientCreateRequest, UUID> {
  Optional<RecipientCreateRequest> findByCaregiver_IdAndIdempotencyKey(UUID caregiverId, String key);
}
