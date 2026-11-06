package com.caregiver.recipient;

import com.caregiver.auth.Caregiver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A care recipient owned by exactly one caregiver. */
@Entity
@Table(name = "care_recipients", indexes = {
    @Index(name = "idx_care_recipients_caregiver", columnList = "caregiver_id"),
    @Index(name = "idx_care_recipients_caregiver_active", columnList = "caregiver_id, active"),
    @Index(name = "idx_care_recipients_lifecycle", columnList = "caregiver_id, lifecycle_status, created_at")
}, uniqueConstraints = @UniqueConstraint(
    name = "uk_care_recipients_id_caregiver", columnNames = {"id", "caregiver_id"}))
public class CareRecipient {
  @Id @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false)
  private Caregiver caregiver;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "relationship", length = 80)
  private String relationship;

  /** Compatibility projection for older API clients; lifecycleStatus is canonical. */
  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_status", nullable = false, length = 32)
  private RecipientLifecycleStatus lifecycleStatus = RecipientLifecycleStatus.ACTIVE;

  @Column(name = "archived_at") private Instant archivedAt;
  @Column(name = "archived_by") private UUID archivedBy;
  @Column(name = "restored_at") private Instant restoredAt;
  @Column(name = "restored_by") private UUID restoredBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CareRecipient() {}

  public CareRecipient(Caregiver caregiver, String name, String relationship) {
    this.caregiver = caregiver;
    this.name = name;
    this.relationship = relationship;
    this.active = true;
    this.lifecycleStatus = RecipientLifecycleStatus.ACTIVE;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public String getName() { return name; }
  public String getRelationship() { return relationship; }
  public boolean isActive() { return active; }
  public RecipientLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
  public Instant getArchivedAt() { return archivedAt; }
  public UUID getArchivedBy() { return archivedBy; }
  public Instant getRestoredAt() { return restoredAt; }
  public UUID getRestoredBy() { return restoredBy; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void update(String name, String relationship) {
    requireActive();
    this.name = name;
    this.relationship = relationship;
  }

  /** Archive is reversible and never touches recipient child records. */
  public void archive(UUID actorId) {
    if (lifecycleStatus == RecipientLifecycleStatus.ARCHIVED) return;
    requireActive();
    this.lifecycleStatus = RecipientLifecycleStatus.ARCHIVED;
    this.active = false;
    this.archivedAt = Instant.now();
    this.archivedBy = actorId;
  }

  /** Restore preserves the most recent archive provenance for historical context. */
  public void restore(UUID actorId) {
    if (lifecycleStatus == RecipientLifecycleStatus.ACTIVE) return;
    if (lifecycleStatus != RecipientLifecycleStatus.ARCHIVED) {
      throw new InactiveRecipientException("This legacy inactive recipient requires review before it can be restored.");
    }
    this.lifecycleStatus = RecipientLifecycleStatus.ACTIVE;
    this.active = true;
    this.restoredAt = Instant.now();
    this.restoredBy = actorId;
  }

  private void requireActive() {
    if (lifecycleStatus != RecipientLifecycleStatus.ACTIVE || !active) {
      throw new InactiveRecipientException();
    }
  }
}
