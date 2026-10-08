package com.caregiver.preview;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Preview: care plan and its versions. Replaced by #9. */
@Entity
@Table(name = "plans")
public class Plan {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "caregiver_id", nullable = false)
  private UUID caregiverId;

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Plan() {
  }

  public Plan(UUID caregiverId, UUID recipientId) {
    this.caregiverId = caregiverId;
    this.recipientId = recipientId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaregiverId() {
    return caregiverId;
  }

  public UUID getRecipientId() {
    return recipientId;
  }
}
