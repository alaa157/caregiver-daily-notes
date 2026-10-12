package com.caregiver.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record. There are intentionally no mutators or delete API. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "actor_caregiver_id", updatable = false)
  private UUID actorCaregiverId;

  @Column(name = "event_type", nullable = false, length = 80, updatable = false)
  private String eventType;

  @Column(name = "target_type", length = 80, updatable = false)
  private String targetType;

  @Column(name = "target_id", length = 160, updatable = false)
  private String targetId;

  @Column(name = "safe_metadata", length = 1000, updatable = false)
  private String safeMetadata;

  @CreationTimestamp
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  public AuditEvent(UUID actorCaregiverId, String eventType, String targetType, String targetId,
      String safeMetadata) {
    this.actorCaregiverId = actorCaregiverId;
    this.eventType = eventType;
    this.targetType = targetType;
    this.targetId = targetId;
    this.safeMetadata = safeMetadata;
  }

  public UUID getId() { return id; }
  public UUID getActorCaregiverId() { return actorCaregiverId; }
  public String getEventType() { return eventType; }
  public String getTargetType() { return targetType; }
  public String getTargetId() { return targetId; }
  public String getSafeMetadata() { return safeMetadata; }
  public Instant getOccurredAt() { return occurredAt; }
}
