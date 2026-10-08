package com.caregiver.preview;

public record AddendumDto(String id, String noteId, String createdAt, String text) {

  static AddendumDto of(Addendum addendum) {
    return new AddendumDto(
        addendum.getId().toString(),
        addendum.getNoteId().toString(),
        addendum.getCreatedAt().toString(),
        addendum.getText());
  }
}
