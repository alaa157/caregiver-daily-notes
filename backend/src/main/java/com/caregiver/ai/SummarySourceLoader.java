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
import com.caregiver.notes.NoteCorrectionEvent;
import com.caregiver.notes.NoteCorrectionEventRepository;
import com.caregiver.notes.NoteSignalService;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
  private final NoteCorrectionEventRepository correctionEvents;
  private final NoteSignalService signals;
  public SummarySourceLoader(CareRecipientRepository recipients, DailyNoteRepository notes,
      NoteAddendumRepository addenda, NoteCorrectionEventRepository correctionEvents, NoteSignalService signals) {
    this.recipients = recipients;
    this.notes = notes;
    this.addenda = addenda;
    this.correctionEvents = correctionEvents;
    this.signals = signals;
  }
  public record Source(List<SummaryNote> notes, List<NoteSignals> signals) {}

  private record CorrectionTimelineEntry(Instant createdAt, boolean structuredEvent, long sequenceNo, UUID id,
      NoteAddendum addendum, NoteCorrectionEvent event) {}

  @Transactional(readOnly = true)
  public Source load(SummaryRequest request) {
    UUID recipientId = UUID.fromString(request.recipientId());
    UUID owner = AuthContext.currentCaregiverId();
    if (recipients.findByIdAndCaregiver_Id(recipientId, owner).isEmpty()) {
      throw new RecipientNotFoundException();
    }
    LocalDate end = request.periodEndInclusive();
    List<DailyNote> selected = notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        owner, recipientId, end.minusDays(request.periodDays() - 1L), end).stream()
        .sorted(Comparator.comparing(DailyNote::getDate).thenComparing(DailyNote::getId))
        .toList();
    List<UUID> noteIds = selected.stream().map(DailyNote::getId).toList();
    Map<UUID, List<NoteAddendum>> addendaByNote = selected.isEmpty() ? Map.of() : addenda
        .findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(noteIds, owner)
        .stream().collect(Collectors.groupingBy(item -> item.getNote().getId()));
    Map<UUID, List<NoteCorrectionEvent>> eventsByNote = selected.isEmpty() ? Map.of() : correctionEvents
        .findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(noteIds, owner)
        .stream().collect(Collectors.groupingBy(event -> event.getNote().getId()));
    List<SummaryNote> texts = selected.stream().map(note -> new SummaryNote(note.getId().toString(), note.getDate(),
        transcript(note, addendaByNote.getOrDefault(note.getId(), List.of()),
            eventsByNote.getOrDefault(note.getId(), List.of())))).toList();
    // Fetch both adjacent trend windows; note transcripts remain limited to the requested period.
    long trendDays = 2L * request.periodDays();
    return new Source(texts, signals.forRange(recipientId, end.minusDays(trendDays - 1), end));
  }
  private String transcript(DailyNote note, List<NoteAddendum> addenda, List<NoteCorrectionEvent> events) {
    StringBuilder text = new StringBuilder("ORIGINAL OBSERVATIONS\n");
    append(text, note.observations());
    List<CorrectionTimelineEntry> timeline = new ArrayList<>(addenda.size() + events.size());
    addenda.forEach(addendum -> timeline.add(new CorrectionTimelineEntry(
        addendum.getCreatedAt(), false, 0, addendum.getId(), addendum, null)));
    events.forEach(event -> timeline.add(new CorrectionTimelineEntry(
        event.getCreatedAt(), true, event.getSequenceNo(), event.getId(), null, event)));
    timeline.sort(Comparator.comparing(CorrectionTimelineEntry::createdAt)
        .thenComparing(CorrectionTimelineEntry::structuredEvent)
        .thenComparingLong(CorrectionTimelineEntry::sequenceNo)
        .thenComparing(CorrectionTimelineEntry::id));
    for (CorrectionTimelineEntry entry : timeline) {
      if (entry.addendum() != null) {
        appendAddendum(text, entry.addendum());
      } else {
        appendCorrectionEvent(text, entry.event());
      }
    }
    return text.toString();
  }
  private void appendAddendum(StringBuilder text, NoteAddendum addendum) {
    text.append("\nAPPENDED ADDENDUM\n")
        .append("id: ").append(addendum.getId()).append('\n')
        .append("createdAt: ").append(addendum.getCreatedAt()).append('\n')
        .append("actorId: ").append(addendum.getCaregiver().getId()).append('\n')
        .append("providedFields: ").append(addendum.observationPresence().fieldNames()).append('\n')
        .append("medicationVerificationProvided: ").append(addendum.medicationVerificationWasProvided()).append('\n');
    append(text, addendum.observations());
  }
  private void appendCorrectionEvent(StringBuilder text, NoteCorrectionEvent event) {
    text.append("\nSTRUCTURED CORRECTION EVENT\n")
        .append("eventId: ").append(event.getId()).append('\n')
        .append("noteId: ").append(event.getNote().getId()).append('\n')
        .append("sequenceNo: ").append(event.getSequenceNo()).append('\n')
        .append("createdAt: ").append(event.getCreatedAt()).append('\n')
        .append("actorId: ").append(event.getCaregiver().getId()).append('\n')
        .append("field: ").append(event.getField()).append('\n')
        .append("operation: ").append(event.getOperation()).append('\n');
    if ("SET".equals(event.getOperation())) {
      text.append("valueJson: ").append(event.getValueJson()).append('\n');
    }
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
