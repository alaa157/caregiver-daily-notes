package com.caregiver.audit;

import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.UUID;

/** Restricted persistence port: append and owner-scoped read only; no update/delete methods. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {
  <S extends AuditEvent> S save(S event);
  List<AuditEvent> findAllByActorCaregiverIdOrderByOccurredAtDesc(UUID actorCaregiverId);
}
