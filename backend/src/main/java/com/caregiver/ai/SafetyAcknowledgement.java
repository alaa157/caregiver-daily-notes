package com.caregiver.ai;

import com.caregiver.auth.Caregiver;
import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/** Immutable record that a caregiver reviewed a safety signal. */
@Entity
@Immutable
@Table(name = "safety_acknowledgements", uniqueConstraints = @UniqueConstraint(
    name = "uk_safety_acknowledgements_period",
    columnNames = {"caregiver_id", "recipient_id", "rule_id", "period_end_inclusive", "period_days"}))
public class SafetyAcknowledgement {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_safety_acknowledgements_caregiver"))
  private Caregiver caregiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_safety_acknowledgements_recipient"))
  private CareRecipient recipient;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_id", length = 80, nullable = false, updatable = false)
  private SafetyRuleId ruleId;

  @Column(name = "period_end_inclusive", nullable = false, updatable = false)
  private LocalDate periodEndInclusive;

  @Column(name = "period_days", nullable = false, updatable = false)
  private int periodDays;

  @CreationTimestamp
  @Column(name = "acknowledged_at", nullable = false, updatable = false)
  private Instant acknowledgedAt;

  protected SafetyAcknowledgement() {
  }

  public SafetyAcknowledgement(Caregiver caregiver, CareRecipient recipient, SafetyRuleId ruleId,
      LocalDate periodEndInclusive, int periodDays) {
    this.caregiver = Objects.requireNonNull(caregiver, "caregiver");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
    Caregiver recipientCaregiver = Objects.requireNonNull(recipient.getCaregiver(), "recipient.caregiver");
    if (caregiver != recipientCaregiver
        && (caregiver.getId() == null || !caregiver.getId().equals(recipientCaregiver.getId()))) {
      throw new IllegalArgumentException("caregiver must own recipient");
    }
    this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
    this.periodEndInclusive = Objects.requireNonNull(periodEndInclusive, "periodEndInclusive");
    if (periodDays <= 0) {
      throw new IllegalArgumentException("periodDays must be positive");
    }
    this.periodDays = periodDays;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public CareRecipient getRecipient() { return recipient; }
  public SafetyRuleId getRuleId() { return ruleId; }
  public LocalDate getPeriodEndInclusive() { return periodEndInclusive; }
  public int getPeriodDays() { return periodDays; }
  public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
