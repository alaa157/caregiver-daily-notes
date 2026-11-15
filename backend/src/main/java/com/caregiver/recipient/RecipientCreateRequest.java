package com.caregiver.recipient;

import com.caregiver.auth.Caregiver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Records the outcome of an idempotent recipient create so a retried
 * {@code POST /api/recipients} replays the original recipient instead of
 * creating a duplicate. Unique on {@code (caregiver_id, idempotency_key)}.
 */
@Entity
@Table(name = "recipient_create_requests", uniqueConstraints = @UniqueConstraint(
    name = "uk_recipient_create_requests_owner_key", columnNames = {"caregiver_id", "idempotency_key"}))
public class RecipientCreateRequest {
  @Id @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false)
  private Caregiver caregiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
  private CareRecipient recipient;

  @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "request_digest", length = 64, nullable = false, updatable = false)
  private String requestDigest;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RecipientCreateRequest() {}

  public RecipientCreateRequest(Caregiver caregiver, CareRecipient recipient,
      String idempotencyKey, String requestDigest) {
    this.caregiver = caregiver;
    this.recipient = recipient;
    this.idempotencyKey = idempotencyKey;
    this.requestDigest = requestDigest;
  }

  public UUID getId() { return id; }
  public Caregiver getCaregiver() { return caregiver; }
  public CareRecipient getRecipient() { return recipient; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getRequestDigest() { return requestDigest; }
  public Instant getCreatedAt() { return createdAt; }
}
