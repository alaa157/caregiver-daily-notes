package com.caregiver.notes;

import com.caregiver.auth.Caregiver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Immutable
@Table(name = "note_correction_batches", uniqueConstraints = @UniqueConstraint(
    name = "uk_note_correction_batch_owner_key", columnNames = {"caregiver_id", "idempotency_key"}))
public class NoteCorrectionBatch {
  @Id @UuidGenerator @Column(nullable = false, updatable = false) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "note_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_note_correction_batches_note"))
  private DailyNote note;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_note_correction_batches_caregiver"))
  private Caregiver caregiver;
  @Column(name = "idempotency_key", length = 100, updatable = false) private String idempotencyKey;
  @Column(name = "request_hash", length = 64, nullable = false, updatable = false) private String requestHash;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  protected NoteCorrectionBatch() { }
  public NoteCorrectionBatch(DailyNote note, Caregiver caregiver, String key, String hash) {
    this.note = note; this.caregiver = caregiver; this.idempotencyKey = key; this.requestHash = hash;
  }
  public UUID getId() { return id; }
  public DailyNote getNote() { return note; }
  public Caregiver getCaregiver() { return caregiver; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getRequestHash() { return requestHash; }
}
