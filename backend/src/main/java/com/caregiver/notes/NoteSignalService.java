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

  public NoteSignalService(
      DailyNoteRepository notes,
      NoteAddendumRepository addenda,
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

    // Latest non-null addendum wins independently for each structured field.
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

    // Any verified same-day contradiction must remain unknown, even if a later
    // addendum exists; unverified reports alone never become confirmed facts.
    List<String> verifiedStatuses = new ArrayList<>();
    if (Boolean.TRUE.equals(note.getMedicationVerified()) && isKnownMedication(note.getMedicationStatus())) {
      verifiedStatuses.add(note.getMedicationStatus());
    }
    for (NoteAddendum addendum : addendaForNote) {
      if (Boolean.TRUE.equals(addendum.getMedicationVerified()) && isKnownMedication(addendum.getMedicationStatus())) {
        verifiedStatuses.add(addendum.getMedicationStatus());
      }
    }
    boolean contradictory = verifiedStatuses.contains("TAKEN") && verifiedStatuses.contains("MISSED");
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

    return new NoteSignalResponse(
        note.getRecipient().getId(),
        note.getDate(),
        fall,
        pain,
        missedMedication,
        medicationUnverified,
        poorAppetite,
        freeText);
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
