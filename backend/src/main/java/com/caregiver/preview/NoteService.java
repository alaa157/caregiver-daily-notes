package com.caregiver.preview;

import com.caregiver.auth.AuthContext;
import com.caregiver.auth.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Preview notes, addenda, and history scoped to the caregiver. Replaced by #30–#32. */
@Service
public class NoteService {

  private final NoteRepository notes;
  private final AddendumRepository addenda;
  private final RecipientService recipients;

  public NoteService(
      NoteRepository notes, AddendumRepository addenda, RecipientService recipients) {
    this.notes = notes;
    this.addenda = addenda;
    this.recipients = recipients;
  }

  @Transactional
  public NoteDto create(CreateNoteRequest request) {
    Recipient recipient = recipients.requireOwned(request.recipientId());
    Note note = Note.of(
        AuthContext.currentCaregiverId(), recipient.getId(), LocalDate.now());
    note.setMood(orEmpty(request.mood()));
    note.setAppetite(orEmpty(request.appetite()));
    note.setSleep(orEmpty(request.sleep()));
    note.setMobility(orEmpty(request.mobility()));
    note.setMedicationTaken(orEmpty(request.medicationTaken()));
    note.setPain(request.pain());
    note.setFall(request.fall());
    note.setText(request.text());
    try {
      return NoteDto.of(notes.saveAndFlush(note));
    } catch (DataIntegrityViolationException e) {
      throw new ValidationException("A note already exists for this recipient today.");
    }
  }

  public NoteDetailDto detail(UUID id) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    Note note = notes.findByIdAndCaregiverId(id, caregiverId)
        .orElseThrow(() -> missing(id));
    return NoteDetailDto.of(
        note, addenda.findByNoteIdAndCaregiverIdOrderByCreatedAtAsc(id, caregiverId));
  }

  @Transactional
  public AddendumDto append(UUID noteId, String text) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    Note note = notes.findByIdAndCaregiverId(noteId, caregiverId)
        .orElseThrow(() -> missing(noteId));
    if (text == null || text.trim().isEmpty()) {
      throw new ValidationException("Addendum text is required.");
    }
    return AddendumDto.of(
        addenda.save(new Addendum(caregiverId, note.getId(), text)));
  }

  public List<NoteDto> history(UUID recipientId, LocalDate from, LocalDate to) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    List<Note> rows =
        recipientId != null
            ? notes.findByCaregiverIdAndRecipientIdOrderByDateDesc(caregiverId, recipientId)
            : notes.findAll().stream()
                .filter(n -> n.getCaregiverId().equals(caregiverId))
                .sorted(Comparator.comparing(Note::getDate).reversed())
                .toList();
    return rows.stream()
        .filter(n -> from == null || !n.getDate().isBefore(from))
        .filter(n -> to == null || !n.getDate().isAfter(to))
        .map(NoteDto::of)
        .toList();
  }

  List<Note> notesForSignals(UUID recipientId) {
    recipients.requireOwned(recipientId);
    return notes.findByCaregiverIdAndRecipientIdOrderByDateDesc(
        AuthContext.currentCaregiverId(), recipientId);
  }

  private RuntimeException missing(UUID id) {
    if (notes.existsById(id)) {
      return new PreviewForbiddenException("Note belongs to another caregiver.");
    }
    return new PreviewNotFoundException("Note not found.");
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
