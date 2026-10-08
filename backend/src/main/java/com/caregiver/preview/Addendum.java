package com.caregiver.preview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Preview: append-only correction linked to one note. Replaced by #31. */
@Entity
@Table(name = "addenda")
public class Addendum {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "caregiver_id", nullable = false)
  private UUID caregiverId;

  @Column(name = "note_id", nullable = false)
  private UUID noteId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "addendum_text", nullable = false)
  private String text = "";

  protected Addendum() {
  }

  public Addendum(UUID caregiverId, UUID noteId, String text) {
    this.caregiverId = caregiverId;
    this.noteId = noteId;
    this.text = text;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaregiverId() {
    return caregiverId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getText() {
    return text;
  }
}
