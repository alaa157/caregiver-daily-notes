package com.caregiver.preview;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Preview: one version in a plan's history. Replaced by #9. */
@Entity
@Table(name = "plan_versions")
public class PlanVersion {

  @Id
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @ElementCollection
  @CollectionTable(name = "plan_version_items", joinColumns = @JoinColumn(name = "version_id"))
  @Column(name = "item", length = 500)
  private List<String> items = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected PlanVersion() {
  }

  public PlanVersion(UUID planId, int version, String status, List<String> items) {
    this.planId = planId;
    this.version = version;
    this.status = status;
    this.items = new ArrayList<>(items);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlanId() {
    return planId;
  }

  public int getVersion() {
    return version;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public List<String> getItems() {
    return items;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
