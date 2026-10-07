package com.caregiver.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Registered caregiver. Email is stored normalized (trimmed, lowercased). */
@Entity
@Table(name = "caregivers")
public class Caregiver {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "email", length = 320, unique = true, nullable = false)
  private String email;

  @Column(name = "password_hash", length = 100, nullable = false)
  private String passwordHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Caregiver() {
  }

  @PrePersist
  @PreUpdate
  void normalizeEmail() {
    if (email != null) {
      // Locale.ROOT: the no-arg toLowerCase() turns INFO into ınfo under a
      // Turkish default locale, breaking the canonical form.
      email = email.trim().toLowerCase(Locale.ROOT);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
