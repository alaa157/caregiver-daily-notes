package com.caregiver.preview;

import java.util.List;

public record NoteDetailDto(
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
    String text,
    List<AddendumDto> addenda) {

  static NoteDetailDto of(Note note, List<Addendum> addenda) {
    NoteDto base = NoteDto.of(note);
    return new NoteDetailDto(
        base.id(),
        base.recipientId(),
        base.date(),
        base.mood(),
        base.appetite(),
        base.sleep(),
        base.mobility(),
        base.medicationTaken(),
        base.pain(),
        base.fall(),
        base.text(),
        addenda.stream().map(AddendumDto::of).toList());
  }
}
