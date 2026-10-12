package com.caregiver.plans;

import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** One immutable plan version. Lifecycle status may change; content never does. */
@Entity
@Table(name = "care_plan_versions",
    uniqueConstraints = @UniqueConstraint(name = "uk_plan_recipient_version",
        columnNames = {"recipient_id", "version_number"}),
    indexes = {
      @Index(name = "idx_plan_recipient_version", columnList = "recipient_id, version_number"),
      @Index(name = "idx_plan_recipient_status", columnList = "recipient_id, status")
    })
public class CarePlan {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_plan_recipient"))
  private CareRecipient recipient;

  @Column(name = "version_number", nullable = false, updatable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlanStatus status;

  @Lob
  @Column(name = "content", nullable = false, updatable = false)
  private String content;

  @Column(name = "source_summary_id", length = 160, updatable = false)
  private String sourceSummaryId;

  @Column(name = "source_plan_id", updatable = false)
  private UUID sourcePlanId;

  @Column(name = "evidence_note_ids", length = 4000, updatable = false)
  private String evidenceNoteIds;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CarePlan() {}

  public CarePlan(CareRecipient recipient, int versionNumber, PlanStatus status, String content,
      String sourceSummaryId, UUID sourcePlanId, String evidenceNoteIds) {
    this.recipient = recipient;
    this.versionNumber = versionNumber;
    this.status = status;
    this.content = content;
    this.sourceSummaryId = sourceSummaryId;
    this.sourcePlanId = sourcePlanId;
    this.evidenceNoteIds = evidenceNoteIds;
  }

  public UUID getId() { return id; }
  public CareRecipient getRecipient() { return recipient; }
  public int getVersionNumber() { return versionNumber; }
  public PlanStatus getStatus() { return status; }
  public String getContent() { return content; }
  public String getSourceSummaryId() { return sourceSummaryId; }
  public UUID getSourcePlanId() { return sourcePlanId; }
  public String getEvidenceNoteIds() { return evidenceNoteIds; }
  public Instant getCreatedAt() { return createdAt; }

  public void changeStatus(PlanStatus next) {
    if (status == next) {
      throw new IllegalPlanTransitionException("Plan is already in state " + status + ".");
    }
    boolean allowed = switch (status) {
      case SUGGESTED -> next == PlanStatus.ACCEPTED || next == PlanStatus.EDITED_AND_ACCEPTED
          || next == PlanStatus.DISMISSED || next == PlanStatus.ARCHIVED;
      case ACCEPTED, EDITED_AND_ACCEPTED -> next == PlanStatus.ARCHIVED;
      case DISMISSED, ARCHIVED -> false;
    };
    if (!allowed) {
      throw new IllegalPlanTransitionException("Transition from " + status + " to " + next + " is not allowed.");
    }
    status = next;
  }
}
