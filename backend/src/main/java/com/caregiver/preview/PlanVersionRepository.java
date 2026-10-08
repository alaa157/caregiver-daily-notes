package com.caregiver.preview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {

  List<PlanVersion> findByPlanIdOrderByVersionDesc(UUID planId);
}
