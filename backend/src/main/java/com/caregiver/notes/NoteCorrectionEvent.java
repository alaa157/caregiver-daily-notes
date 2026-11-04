package com.caregiver.notes;

import com.caregiver.auth.Caregiver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "note_correction_events", uniqueConstraints = @UniqueConstraint(
    name = "uk_note_correction_event_sequence", columnNames = {"note_id", "sequence_no"}),
    indexes = @Index(name = "idx_note_correction_events_owner_sequence", columnList = "caregiver_id,note_id,sequence_no"))
public class NoteCorrectionEvent {
  @Id @UuidGenerator @Column(nullable = false, updatable = false) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_note_correction_events_batch"))
  private NoteCorrectionBatch batch;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "note_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_note_correction_events_note"))
  private DailyNote note;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_note_correction_events_caregiver"))
  private Caregiver caregiver;
  @Column(name = "sequence_no", nullable = false, updatable = false) private long sequenceNo;
  @Column(name = "field_key", length = 32, nullable = false, updatable = false) private String field;
  @Column(name = "operation", length = 20, nullable = false, updatable = false) private String operation;
  @Column(name = "value_json", columnDefinition = "TEXT", updatable = false) private String valueJson;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  protected NoteCorrectionEvent() { }
  public NoteCorrectionEvent(NoteCorrectionBatch batch, DailyNote note, Caregiver caregiver,
      long sequenceNo, String field, CorrectionOperation operation, String valueJson) {
    this.batch = batch; this.note = note; this.caregiver = caregiver; this.sequenceNo = sequenceNo;
    this.field = field; this.operation = operation.name(); this.valueJson = valueJson;
  }
  public UUID getId() { return id; }
  public NoteCorrectionBatch getBatch() { return batch; }
  public DailyNote getNote() { return note; }
  public Caregiver getCaregiver() { return caregiver; }
  public long getSequenceNo() { return sequenceNo; }
  public String getField() { return field; }
  public String getOperation() { return operation; }
  public String getValueJson() { return valueJson; }
  public Instant getCreatedAt() { return createdAt; }
}
