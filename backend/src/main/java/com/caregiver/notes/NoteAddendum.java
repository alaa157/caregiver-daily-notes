package com.caregiver.notes;

import com.caregiver.auth.Caregiver;
import com.caregiver.common.NoteObservations;
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
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "note_addenda",
    indexes = @Index(name = "idx_note_addenda_note_created", columnList = "note_id,created_at"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_note_addenda_owner_idempotency",
        columnNames = {"caregiver_id", "idempotency_key"}))
public class NoteAddendum extends ObservationColumns {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "note_id", nullable = false, updatable = false)
  private DailyNote note;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false)
  private Caregiver caregiver;

  @Column(name = "idempotency_key", length = 100, updatable = false)
  private String idempotencyKey;

  @Column(name = "mood_provided", nullable = false, updatable = false)
  private boolean moodProvided;
  @Column(name = "appetite_provided", nullable = false, updatable = false)
  private boolean appetiteProvided;
  @Column(name = "sleep_provided", nullable = false, updatable = false)
  private boolean sleepProvided;
  @Column(name = "mobility_provided", nullable = false, updatable = false)
  private boolean mobilityProvided;
  @Column(name = "medication_status_provided", nullable = false, updatable = false)
  private boolean medicationStatusProvided;
  @Column(name = "pain_score_provided", nullable = false, updatable = false)
  private boolean painScoreProvided;
  @Column(name = "fall_reported_provided", nullable = false, updatable = false)
  private boolean fallReportedProvided;
  @Column(name = "free_text_provided", nullable = false, updatable = false)
  private boolean freeTextProvided;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected NoteAddendum() {}

  public NoteAddendum(
      DailyNote note,
      Caregiver caregiver,
      NoteObservations observations,
      ObservationPresence observationPresence,
      boolean medicationVerificationProvided,
      String idempotencyKey) {
    super(observations, medicationVerificationProvided);
    this.note = note;
    this.caregiver = caregiver;
    this.idempotencyKey = idempotencyKey;
    this.moodProvided = observationPresence.mood();
    this.appetiteProvided = observationPresence.appetite();
    this.sleepProvided = observationPresence.sleep();
    this.mobilityProvided = observationPresence.mobility();
    this.medicationStatusProvided = observationPresence.medicationStatus();
    this.painScoreProvided = observationPresence.painScore();
    this.fallReportedProvided = observationPresence.fallReported();
    this.freeTextProvided = observationPresence.freeText();
  }

  public UUID getId() { return id; }
  public DailyNote getNote() { return note; }
  public Caregiver getCaregiver() { return caregiver; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public Instant getCreatedAt() { return createdAt; }
  public ObservationPresence observationPresence() {
    return new ObservationPresence(
        moodProvided, appetiteProvided, sleepProvided, mobilityProvided,
        medicationStatusProvided, painScoreProvided, fallReportedProvided, freeTextProvided);
  }
}
