package com.caregiver.preview;

import com.caregiver.auth.AuthContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Preview deterministic signals and template summaries derived from stored
 * notes only — no LLM calls, nothing inferred. Replaced by #34 and the AI
 * track. Preview simplification: addenda carry free text only, so the
 * latest addendum text is appended to the signal text rather than applied
 * per field.
 */
@Service
public class SignalService {

  private final NoteService notes;
  private final AddendumRepository addenda;

  public SignalService(NoteService notes, AddendumRepository addenda) {
    this.notes = notes;
    this.addenda = addenda;
  }

  public List<SignalDto> signals(UUID recipientId, LocalDate from, LocalDate to) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return notes.notesForSignals(recipientId).stream()
        .filter(n -> from == null || !n.getDate().isBefore(from))
        .filter(n -> to == null || !n.getDate().isAfter(to))
        .sorted(Comparator.comparing(Note::getDate))
        .map(n -> toSignal(n, caregiverId))
        .toList();
  }

  public SummaryDto summarize(UUID recipientId, int periodDays) {
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(periodDays - 1);
    List<SignalDto> signals = signals(recipientId, from, to);
    List<String> flags = new ArrayList<>();
    List<SummaryDto.EvidenceDto> evidence = new ArrayList<>();
    List<SummaryDto.UncertaintyDto> uncertainties = new ArrayList<>();
    for (SignalDto signal : signals) {
      if (signal.fallReported() && !flags.contains("FALL_REPORTED")) {
        flags.add("FALL_REPORTED");
      }
      if (signal.pain() != null && signal.pain() >= 7 && !flags.contains("HIGH_PAIN")) {
        flags.add("HIGH_PAIN");
      }
      if (signal.medicationUnverified() && !flags.contains("MEDICATION_UNCLEAR")) {
        flags.add("MEDICATION_UNCLEAR");
        uncertainties.add(
            new SummaryDto.UncertaintyDto(
                "medication", "Caregiver unsure whether medication was taken on " + signal.date() + "."));
      }
    }
    for (SignalDto signal : signals) {
      String quote = signal.text().length() > 60 ? signal.text().substring(0, 60) : signal.text();
      if (!quote.isBlank()) {
        evidence.add(new SummaryDto.EvidenceDto(signal.noteId(), quote));
      }
    }
    String text =
        signals.isEmpty()
            ? "No notes in range."
            : "Reviewed " + signals.size() + " day(s) of notes.";
    return new SummaryDto(
        "preview-" + recipientId + "-" + periodDays,
        recipientId.toString(),
        periodDays,
        text,
        flags,
        evidence,
        uncertainties);
  }

  private SignalDto toSignal(Note note, UUID caregiverId) {
    List<Addendum> rows =
        addenda.findByNoteIdAndCaregiverIdOrderByCreatedAtAsc(note.getId(), caregiverId);
    String text = note.getText();
    if (!rows.isEmpty()) {
      text = text + "\n[Correction] " + rows.get(rows.size() - 1).getText();
    }
    String medication = medicationOf(note.getMedicationTaken());
    return new SignalDto(
        note.getId().toString(),
        note.getRecipientId().toString(),
        note.getDate().toString(),
        note.isFall(),
        note.getPain(),
        medication,
        !(medication.equals("taken") || medication.equals("missed")),
        "poor".equalsIgnoreCase(note.getAppetite()),
        text);
  }

  private static String medicationOf(String raw) {
    if (raw == null) {
      return "unknown";
    }
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if (value.equals("taken")) {
      return "taken";
    }
    if (value.equals("missed") || value.equals("not-taken") || value.equals("not taken")) {
      return "missed";
    }
    return "unknown";
  }
}
