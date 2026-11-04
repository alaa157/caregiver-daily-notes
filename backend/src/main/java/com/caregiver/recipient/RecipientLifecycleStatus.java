package com.caregiver.recipient;

/**
 * Explicit Web recipient lifecycle. Historical inactive rows whose original
 * meaning is not evidenced remain in LEGACY_INACTIVE_REVIEW until reviewed.
 */
public enum RecipientLifecycleStatus {
  ACTIVE,
  ARCHIVED,
  LEGACY_INACTIVE_REVIEW
}
