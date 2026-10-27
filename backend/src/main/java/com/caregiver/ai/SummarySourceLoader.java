package com.caregiver.ai;

import com.caregiver.auth.AuthContext;
import com.caregiver.common.NoteSignals;
import com.caregiver.notes.DailyNote;
import com.caregiver.notes.DailyNoteRepository;
import com.caregiver.notes.NoteAddendum;
import com.caregiver.notes.NoteAddendumRepository;
import com.caregiver.notes.NoteSignalService;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads owned observations in one short read transaction, before any provider call. */
@Service
public class SummarySourceLoader {
  private final CareRecipientRepository recipients;
  private final DailyNoteRepository notes;
  private final NoteAddendumRepository addenda;
  private final NoteSignalService signals;
  public SummarySourceLoader(CareRecipientRepository recipients, DailyNoteRepository notes,
      NoteAddendumRepository addenda, NoteSignalService signals) {
    this.recipients = recipients; this.notes = notes; this.addenda = addenda; this.signals = signals;
  }
  public record Source(List<SummaryNote> notes, List<NoteSignals> signals) {}

  @Transactional(readOnly = true)
  public Source load(SummaryRequest request) {
    UUID recipientId = UUID.fromString(request.recipientId());
    UUID owner = AuthContext.currentCaregiverId();
    recipients.findByIdAndCaregiver_Id(recipientId, owner).orElseThrow(RecipientNotFoundException::new);
    LocalDate end = request.periodEndInclusive();
    List<DailyNote> found = notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        owner, recipientId, end.minusDays(request.periodDays() - 1L), end);
    Map<UUID, List<NoteAddendum>> grouped = found.isEmpty() ? Map.of() : addenda
        .findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(found.stream().map(DailyNote::getId).toList(), owner)
        .stream().collect(Collectors.groupingBy(item -> item.getNote().getId()));
    List<SummaryNote> texts = found.stream().map(note -> new SummaryNote(note.getId().toString(), note.getDate(),
        transcript(note, grouped.getOrDefault(note.getId(), List.of())))).toList();
    // Trends compare the latest two seven-day windows, including for a seven-day summary.
    return new Source(texts, signals.forRange(recipientId, end.minusDays(Math.max(14, request.periodDays()) - 1L), end));
  }
  private String transcript(DailyNote note, List<NoteAddendum> corrections) {
    StringBuilder text = new StringBuilder("ORIGINAL OBSERVATIONS\n");
    append(text, note.getMood(), note.getAppetite(), note.getSleep(), note.getMobility(),
        note.getMedicationStatus(), note.getMedicationVerified(), note.getPainScore(), note.getFallReported(), note.getFreeText());
    for (NoteAddendum correction : corrections) {
      text.append("\nAPPENDED CORRECTION ").append(correction.getId()).append("\n");
      append(text, correction.getMood(), correction.getAppetite(), correction.getSleep(), correction.getMobility(),
          correction.getMedicationStatus(), correction.getMedicationVerified(), correction.getPainScore(), correction.getFallReported(), correction.getFreeText());
    }
    return text.toString();
  }
  private void append(StringBuilder text, String mood, String appetite, String sleep, String mobility,
      String medication, Boolean verified, Integer pain, Boolean fall, String freeText) {
    field(text, "mood", mood); field(text, "appetite", appetite); field(text, "sleep", sleep);
    field(text, "mobility", mobility); field(text, "medicationStatus", medication);
    field(text, "medicationVerified", verified); field(text, "painScore", pain); field(text, "fallReported", fall);
    if (freeText != null) text.append("Free text (verbatim):\n").append(freeText).append('\n');
  }
  private void field(StringBuilder text, String key, Object value) {
    if (value != null) text.append(key).append(": ").append(value).append('\n');
  }
}
