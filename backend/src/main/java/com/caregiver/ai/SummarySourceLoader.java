package com.caregiver.ai;

import com.caregiver.auth.AuthContext;
import com.caregiver.common.NoteSignals;
import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
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
    append(text, note.observations());
    for (NoteAddendum correction : corrections) {
      text.append("\nAPPENDED CORRECTION ").append(correction.getId()).append("\n");
      append(text, correction.observations());
    }
    return text.toString();
  }

  private void append(StringBuilder text, NoteObservations observations) {
    field(text, "mood", observations.mood());
    field(text, "appetite", observations.appetite());
    field(text, "sleep", observations.sleep());
    field(text, "mobility", observations.mobility());
    field(text, "medicationStatus", observations.medicationStatus());
    field(text, "painScore", observations.painScore());
    field(text, "fallReported", observations.fallReported());
    field(text, "freeText", observations.freeText());
    text.append("medicationVerification: ").append(observations.medicationVerification()).append('\n');
  }

  private void field(StringBuilder text, String key, Observation<?> observation) {
    text.append(key).append(" [").append(observation.state()).append(']');
    if (observation.state() == ObservationState.RECORDED) {
      text.append(": ").append(observation.value());
    } else if (observation.state() == ObservationState.LEGACY_AMBIGUOUS) {
      text.append(": historical value withheld; meaning unresolved");
    }
    text.append('\n');
  }
}
