package com.caregiver.preview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Preview: care recipient owned by one caregiver. Replaced by #8. */
@Entity
@Table(name = "recipients")
public class Recipient {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "caregiver_id", nullable = false)
  private UUID caregiverId;

  @Column(name = "name", length = 320, nullable = false)
  private String name;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Recipient() {
  }

  public Recipient(UUID caregiverId, String name, boolean active) {
    this.caregiverId = caregiverId;
    this.name = name;
    this.active = active;
  }

  @PrePersist
  @PreUpdate
  void normalizeName() {
    if (name != null) {
      name = name.trim();
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaregiverId() {
    return caregiverId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
