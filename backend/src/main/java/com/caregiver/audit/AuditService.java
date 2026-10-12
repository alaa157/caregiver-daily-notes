package com.caregiver.audit;

import com.caregiver.auth.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {
  private final AuditEventRepository events;

  public AuditService(AuditEventRepository events) {
    this.events = events;
  }

  /** Actor identity is derived from the verified security context for ordinary protected writes. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String eventType, String targetType, String targetId, String safeMetadata) {
    recordForActor(AuthContext.currentCaregiverId(), eventType, targetType, targetId, safeMetadata);
  }

  /**
   * Used only by authentication flows after they have verified or created the caregiver.
   * The actor ID must come from the persisted caregiver, never from request input.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordForActor(UUID actorCaregiverId, String eventType, String targetType,
      String targetId, String safeMetadata) {
    String metadata = safeMetadata;
    if (metadata != null) {
      metadata = metadata.replaceAll(
          "(?i)(password|token|secret|api[-_ ]?key)\\\\s*[:=]\\\\s*[^;, ]+",
          "$1=[REDACTED]");
      if (metadata.length() > 1000) metadata = metadata.substring(0, 1000);
    }
    events.save(new AuditEvent(actorCaregiverId, eventType, targetType, targetId, metadata));
  }
}
