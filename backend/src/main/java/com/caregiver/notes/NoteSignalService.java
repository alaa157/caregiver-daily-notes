package com.caregiver.notes;

import com.caregiver.auth.AuthContext;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    List<DailyNote> found = notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        caregiverId, recipientId, from, to);
    // One batched addenda read for the whole range instead of one query per note.
    Map<UUID, List<NoteAddendum>> addendaByNote = Map.of();
    if (!found.isEmpty()) {
      Collection<UUID> noteIds = found.stream().map(DailyNote::getId).toList();
      addendaByNote = addenda
          .findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(noteIds, caregiverId)
          .stream()
          .collect(Collectors.groupingBy(addendum -> addendum.getNote().getId()));
    }
    final Map<UUID, List<NoteAddendum>> grouped = addendaByNote;
    return found.stream()
        .map(note -> merge(note, grouped.getOrDefault(note.getId(), List.of())))
        .toList();
  }

  /**
   * Merges one note with its chronological addenda into a single signal.
   *
   * <p><b>Medication contradiction policy (issue #34, conservative by design):</b>
   * historical observations are never rewritten — the note and every addendum
   * stay persisted — but the <em>interpreted</em> signal must not present
   * uncertain information as a confirmed fact. Per-field latest-non-null
   * merging applies first (a correction supersedes earlier values for
   * display), yet when the combined history for the day contains both a
   * {@code TAKEN} and a {@code MISSED} report the interpreted state collapses
   * to {@code missedMedication=null} with {@code medicationUnverified=true}.
   * The policy is symmetric: it applies identically whether the original note
   * said {@code MISSED} and the addendum says {@code TAKEN}, or the reverse.
   * A bare {@code UNKNOWN} report contributes no contradiction evidence on its
   * own; it simply leaves the state unverified. Downstream safety rules must
   * treat {@code medicationUnverified=true} as "do not assert", never as a
   * confirmed missed (or taken) dose.
   */
  private NoteSignalResponse merge(DailyNote note, List<NoteAddendum> addendaForNote) {
    String mood = note.getMood();
    String appetite = note.getAppetite();
    String sleep = note.getSleep();
    String mobility = note.getMobility();
    Boolean fall = note.getFallReported();
    Boolean poorAppetite = poor(appetite);
    Integer pain = note.getPainScore();
    String medication = note.getMedicationStatus();
    Boolean medicationVerified = note.getMedicationVerified();
    String freeText = note.getFreeText();

    // Latest non-null addendum wins independently for each field.
    for (NoteAddendum addendum : addendaForNote) {
      if (addendum.getMood() != null) mood = addendum.getMood();
      if (addendum.getAppetite() != null) appetite = addendum.getAppetite();
      if (addendum.getSleep() != null) sleep = addendum.getSleep();
      if (addendum.getMobility() != null) mobility = addendum.getMobility();
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

    return new NoteSignalResponse(note.getRecipient().getId(), note.getDate(), mood, appetite, sleep, mobility,
        medication, fall, pain, missedMedication, medicationUnverified, poorAppetite, freeText);
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
