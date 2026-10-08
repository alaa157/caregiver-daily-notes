package com.caregiver.preview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Preview: one daily note per recipient per day. Replaced by #30. */
@Entity
@Table(name = "notes")
public class Note {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "caregiver_id", nullable = false)
  private UUID caregiverId;

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "note_date", nullable = false)
  private LocalDate date;

  @Column(name = "mood", length = 32)
  private String mood = "";

  @Column(name = "appetite", length = 32)
  private String appetite = "";

  @Column(name = "sleep", length = 32)
  private String sleep = "";

  @Column(name = "mobility", length = 32)
  private String mobility = "";

  @Column(name = "medication_taken", length = 32)
  private String medicationTaken = "";

  @Column(name = "pain", nullable = false)
  private int pain;

  @Column(name = "fall", nullable = false)
  private boolean fall;

  @Column(name = "note_text", nullable = false)
  private String text = "";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Note() {
  }

  public static Note of(UUID caregiverId, UUID recipientId, LocalDate date) {
    Note note = new Note();
    note.caregiverId = caregiverId;
    note.recipientId = recipientId;
    note.date = date;
    return note;
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

  public LocalDate getDate() {
    return date;
  }

  public String getMood() {
    return mood;
  }

  public void setMood(String mood) {
    this.mood = mood;
  }

  public String getAppetite() {
    return appetite;
  }

  public void setAppetite(String appetite) {
    this.appetite = appetite;
  }

  public String getSleep() {
    return sleep;
  }

  public void setSleep(String sleep) {
    this.sleep = sleep;
  }

  public String getMobility() {
    return mobility;
  }

  public void setMobility(String mobility) {
    this.mobility = mobility;
  }

  public String getMedicationTaken() {
    return medicationTaken;
  }

  public void setMedicationTaken(String medicationTaken) {
    this.medicationTaken = medicationTaken;
  }

  public int getPain() {
    return pain;
  }

  public void setPain(int pain) {
    this.pain = pain;
  }

  public boolean isFall() {
    return fall;
  }

  public void setFall(boolean fall) {
    this.fall = fall;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
