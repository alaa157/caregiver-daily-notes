package com.caregiver.preview;

public record RecipientDto(String id, String name, boolean active) {

  static RecipientDto of(Recipient recipient) {
    return new RecipientDto(
        recipient.getId().toString(), recipient.getName(), recipient.isActive());
  }
}
