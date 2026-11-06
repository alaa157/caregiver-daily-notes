package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A caregiver-maintained medication identity and schedule for one recipient. */
@Entity
@Table(name = "recipient_medications", indexes = @Index(
    name = "idx_recipient_medications_owner_recipient", columnList = "caregiver_id, recipient_id"))
public class MedicationRecord {
  @Id
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_medications_caregiver"))
  private Caregiver caregiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_medications_recipient_owner"))
  private CareRecipient recipient;

  @Column(name = "display_name", nullable = false, columnDefinition = "text")
  private String displayName;

  @Column(name = "dose_text", nullable = false, columnDefinition = "text")
  private String doseText;

  @Column(name = "schedule_text", nullable = false, columnDefinition = "text")
  private String scheduleText;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected MedicationRecord() {
  }

  public MedicationRecord(Caregiver caregiver, CareRecipient recipient, String displayName,
      String doseText, String scheduleText) {
    this(caregiver, recipient, displayName, doseText, scheduleText, true);
  }

  public MedicationRecord(Caregiver caregiver, CareRecipient recipient, String displayName,
      String doseText, String scheduleText, boolean active) {
    this.caregiver = Objects.requireNonNull(caregiver, "caregiver");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
    Caregiver recipientCaregiver = Objects.requireNonNull(recipient.getCaregiver(), "recipient.caregiver");
    if (caregiver != recipientCaregiver
        && (caregiver.getId() == null || !caregiver.getId().equals(recipientCaregiver.getId()))) {
      throw new IllegalArgumentException("caregiver must own recipient");
    }
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.doseText = Objects.requireNonNull(doseText, "doseText");
    this.scheduleText = Objects.requireNonNull(scheduleText, "scheduleText");
    this.active = active;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public CareRecipient getRecipient() { return recipient; }
  public String getDisplayName() { return displayName; }
  public String getDoseText() { return doseText; }
  public String getScheduleText() { return scheduleText; }
  public boolean isActive() { return active; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
