package com.caregiver.recipient;

import com.caregiver.auth.Caregiver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A care recipient owned by exactly one caregiver. */
@Entity
@Table(
    name = "care_recipients",
    indexes = {
      @Index(name = "idx_care_recipients_caregiver", columnList = "caregiver_id"),
      @Index(name = "idx_care_recipients_caregiver_active", columnList = "caregiver_id, active")
    })
public class CareRecipient {

  @Id
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false)
  private Caregiver caregiver;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "relationship", length = 80)
  private String relationship;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CareRecipient() {
  }

  public CareRecipient(Caregiver caregiver, String name, String relationship) {
    this.caregiver = caregiver;
    this.name = name;
    this.relationship = relationship;
    this.active = true;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public String getName() { return name; }
  public String getRelationship() { return relationship; }
  public boolean isActive() { return active; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void update(String name, String relationship) {
    this.name = name;
    this.relationship = relationship;
  }

  /** Soft-deactivate only; related notes and plans are not cascaded or deleted. */
  public void deactivate() {
    this.active = false;
  }
}
