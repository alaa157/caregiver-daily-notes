package com.caregiver.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {

  Optional<Caregiver> findByEmail(String email);

  boolean existsByEmail(String email);
}
