package com.caregiver.notes;

import com.caregiver.auth.Caregiver;
import com.caregiver.common.NoteObservations;
import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "daily_notes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_note_recipient_date", columnNames = {"recipient_id", "note_date"}),
        @UniqueConstraint(name = "uk_daily_note_owner_idempotency", columnNames = {"caregiver_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "idx_daily_notes_owner_date", columnList = "caregiver_id,note_date"),
        @Index(name = "idx_daily_notes_recipient_date", columnList = "recipient_id,note_date")
    })
public class DailyNote extends ObservationColumns {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false)
  private Caregiver caregiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
  private CareRecipient recipient;

  @Column(name = "note_date", nullable = false, updatable = false)
  private LocalDate date;

  @Column(name = "idempotency_key", length = 100, updatable = false)
  private String idempotencyKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DailyNote() {}

  public DailyNote(
      Caregiver caregiver,
      CareRecipient recipient,
      LocalDate date,
      NoteObservations observations,
      boolean medicationVerificationProvided,
      String idempotencyKey) {
    super(observations, medicationVerificationProvided);
    this.caregiver = caregiver;
    this.recipient = recipient;
    this.date = date;
    this.idempotencyKey = idempotencyKey;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public CareRecipient getRecipient() { return recipient; }
  public LocalDate getDate() { return date; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public Instant getCreatedAt() { return createdAt; }
}
