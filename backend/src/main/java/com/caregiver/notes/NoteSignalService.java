package com.caregiver.notes;

import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NoteSignalService {
  private final DailyNoteRepository notes;
  private final NoteAddendumRepository addenda;
  private final CareRecipientRepository recipients;

  public NoteSignalService(DailyNoteRepository notes, NoteAddendumRepository addenda,
      CareRecipientRepository recipients) {
    this.notes = notes;
    this.addenda = addenda;
    this.recipients = recipients;
  }

  @Transactional(readOnly = true)
  public List<NoteSignalResponse> forRange(UUID recipientId, LocalDate from, LocalDate to) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    if (recipientId == null || from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("recipientId, from and to are required, and from must be on or before to.");
    }
    if (recipients.findByIdAndCaregiver_Id(recipientId, caregiverId).isEmpty()) {
      throw new RecipientNotFoundException();
    }
    return notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
            caregiverId, recipientId, from, to)
        .stream()
        .map(note -> merge(note,
            addenda.findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAsc(note.getId(), caregiverId)))
        .toList();
  }

  private NoteSignalResponse merge(DailyNote note, List<NoteAddendum> addendaForNote) {
    Boolean fall = note.getFallReported();
    Boolean poorAppetite = poor(note.getAppetite());
    Integer pain = note.getPainScore();
    String medication = note.getMedicationStatus();
    Boolean medicationVerified = note.getMedicationVerified();
    String freeText = note.getFreeText();

    // Latest non-null addendum wins independently for each field.
    for (NoteAddendum addendum : addendaForNote) {
      if (addendum.getFallReported() != null) fall = addendum.getFallReported();
      if (addendum.getPainScore() != null) pain = addendum.getPainScore();
      if (addendum.getAppetite() != null) poorAppetite = poor(addendum.getAppetite());
      if (addendum.getMedicationStatus() != null) {
        medication = addendum.getMedicationStatus();
        medicationVerified = addendum.getMedicationVerified();
      }
      if (addendum.getFreeText() != null) freeText = addendum.getFreeText();
    }

    // Conflicting same-day TAKEN/MISSED reports are ambiguous regardless of
    // verification state; do not turn them into a positive missed-medication fact.
    List<String> reportedStatuses = new ArrayList<>();
    if (isKnownMedication(note.getMedicationStatus())) reportedStatuses.add(note.getMedicationStatus());
    for (NoteAddendum addendum : addendaForNote) {
      if (isKnownMedication(addendum.getMedicationStatus())) reportedStatuses.add(addendum.getMedicationStatus());
    }
    boolean contradictory = reportedStatuses.contains("TAKEN") && reportedStatuses.contains("MISSED");
    if (contradictory) {
      medication = "UNKNOWN";
      medicationVerified = false;
    }

    boolean medicationUnverified = medication != null
        && (!Boolean.TRUE.equals(medicationVerified) || "UNKNOWN".equals(medication));
    Boolean missedMedication = null;
    if (!medicationUnverified) {
      if ("MISSED".equals(medication)) missedMedication = true;
      else if ("TAKEN".equals(medication)) missedMedication = false;
    }

    return new NoteSignalResponse(note.getRecipient().getId(), note.getDate(), fall, pain,
        missedMedication, medicationUnverified, poorAppetite, freeText);
  }

  private boolean isKnownMedication(String value) {
    return "TAKEN".equals(value) || "MISSED".equals(value);
  }

  private Boolean poor(String value) {
    if (value == null) return null;
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "POOR", "LOW", "POOR_APPETITE" -> true;
      case "GOOD", "NORMAL", "FAIR" -> false;
      default -> null;
    };
  }
}
