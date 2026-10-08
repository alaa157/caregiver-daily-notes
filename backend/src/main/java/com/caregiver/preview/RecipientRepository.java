package com.caregiver.preview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

  List<Recipient> findByCaregiverId(UUID caregiverId);

  Optional<Recipient> findByIdAndCaregiverId(UUID id, UUID caregiverId);
}
