package com.caregiver.audit;

import com.caregiver.auth.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final AuditEventRepository events;

  public AuditService(AuditEventRepository events) {
    this.events = events;
  }

  /** Must join the caller's transaction so failed writes do not leave success events. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String eventType, String targetType, String targetId, String safeMetadata) {
    String metadata = safeMetadata == null ? null : safeMetadata;
    if (metadata != null) {
      metadata = metadata.replaceAll("(?i)(password|token|secret|api[-_ ]?key)\\s*[:=]\\s*[^;, ]+", "$1=[REDACTED]");
      if (metadata.length() > 1000) metadata = metadata.substring(0, 1000);
    }
    events.save(new AuditEvent(AuthContext.currentCaregiverId(), eventType, targetType, targetId, metadata));
  }
}
