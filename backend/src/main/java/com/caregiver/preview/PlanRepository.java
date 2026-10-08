package com.caregiver.preview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

  List<Plan> findByCaregiverId(UUID caregiverId);

  Optional<Plan> findByIdAndCaregiverId(UUID id, UUID caregiverId);
}
