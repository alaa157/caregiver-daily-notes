package com.caregiver.preview;

public record NoteDto(
    String id,
    String recipientId,
    String date,
    String mood,
    String appetite,
    String sleep,
    String mobility,
    String medicationTaken,
    int pain,
    boolean fall,
    String text) {

  static NoteDto of(Note note) {
    return new NoteDto(
        note.getId().toString(),
        note.getRecipientId().toString(),
        note.getDate().toString(),
        note.getMood(),
        note.getAppetite(),
        note.getSleep(),
        note.getMobility(),
        note.getMedicationTaken(),
        note.getPain(),
        note.isFall(),
        note.getText());
  }
}
