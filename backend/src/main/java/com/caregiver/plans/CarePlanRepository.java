package com.caregiver.plans;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
  List<CarePlan> findAllByRecipient_IdOrderByVersionNumberDesc(UUID recipientId);
  Optional<CarePlan> findFirstByRecipient_IdAndStatusInOrderByVersionNumberDesc(
      UUID recipientId, List<PlanStatus> statuses);
  Optional<CarePlan> findByIdAndRecipient_Id(UUID id, UUID recipientId);

  @Query("select p from CarePlan p where p.recipient.id = :recipientId and p.status in :statuses order by p.versionNumber desc")
  List<CarePlan> findAcceptedByRecipient(@Param("recipientId") UUID recipientId,
      @Param("statuses") List<PlanStatus> statuses);

  @Query("select coalesce(max(p.versionNumber), 0) from CarePlan p where p.recipient.id = :recipientId")
  int maxVersionForRecipient(@Param("recipientId") UUID recipientId);
}
