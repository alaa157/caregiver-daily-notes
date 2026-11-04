package com.caregiver.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverPrincipal;
import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
import com.caregiver.notes.CorrectionOperation;
import com.caregiver.notes.DailyNote;
import com.caregiver.notes.DailyNoteRepository;
import com.caregiver.notes.NoteAddendum;
import com.caregiver.notes.NoteAddendumRepository;
import com.caregiver.notes.NoteCorrectionBatch;
import com.caregiver.notes.NoteCorrectionEvent;
import com.caregiver.notes.NoteCorrectionEventRepository;
import com.caregiver.notes.NoteSignalService;
import com.caregiver.notes.ObservationPresence;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SummarySourceLoaderTest {
  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID RECIPIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final LocalDate END = LocalDate.of(2026, 10, 30);
  private static final Instant CREATED_AT = Instant.parse("2026-10-30T12:00:00Z");

  @Mock private CareRecipientRepository recipients;
  @Mock private DailyNoteRepository notes;
  @Mock private NoteAddendumRepository addenda;
  @Mock private NoteCorrectionEventRepository correctionEvents;
  @Mock private NoteSignalService signals;

  private Caregiver caregiver;
  private CareRecipient recipient;
  private SummarySourceLoader loader;

  @BeforeEach
  void setUp() {
    caregiver = mock(Caregiver.class);
    recipient = mock(CareRecipient.class);
    loader = new SummarySourceLoader(recipients, notes, addenda, correctionEvents, signals);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new CaregiverPrincipal(OWNER_ID), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    when(recipients.findByIdAndCaregiver_Id(RECIPIENT_ID, OWNER_ID)).thenReturn(Optional.of(recipient));
    when(signals.forRange(any(), any(), any())).thenReturn(List.of());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("loads inclusive note dates and preserves the wider trend range")
  void loadsInclusiveNoteDatesAndPreservesTheWiderTrendRange() {
    LocalDate first = END.minusDays(6);
    DailyNote sameDateLaterId = note("later-id", first, originalObservations());
    DailyNote sameDateEarlierId = note("earlier-id", first, originalObservations());
    DailyNote last = note("last-date", END, originalObservations());
    when(notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        OWNER_ID, RECIPIENT_ID, first, END)).thenReturn(List.of(last, sameDateLaterId, sameDateEarlierId));
    List<UUID> orderedIds = List.of(last, sameDateLaterId, sameDateEarlierId).stream()
        .sorted(Comparator.comparing(DailyNote::getDate).thenComparing(DailyNote::getId))
        .map(DailyNote::getId)
        .toList();
    when(addenda.findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(orderedIds, OWNER_ID))
        .thenReturn(List.of());
    when(correctionEvents.findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(orderedIds, OWNER_ID))
        .thenReturn(List.of());

    SummarySourceLoader.Source source = loader.load(request(7));

    assertThat(source.notes()).extracting(SummaryNote::noteId)
        .containsExactlyElementsOf(orderedIds.stream().map(UUID::toString).toList());
    assertThat(source.notes()).extracting(SummaryNote::date).containsExactly(first, first, END);
    verify(recipients).findByIdAndCaregiver_Id(RECIPIENT_ID, OWNER_ID);
    verify(notes).findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        OWNER_ID, RECIPIENT_ID, first, END);
    verify(addenda).findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(orderedIds, OWNER_ID);
    verify(correctionEvents).findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(orderedIds, OWNER_ID);
    verify(correctionEvents, times(1))
        .findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(orderedIds, OWNER_ID);
    verify(signals).forRange(RECIPIENT_ID, END.minusDays(13), END);
  }

  @Test
  @DisplayName("preserves original observations, addendum provenance, and structured corrections")
  void preservesOriginalObservationsAddendumProvenanceAndStructuredCorrections() {
    when(caregiver.getId()).thenReturn(OWNER_ID);
    DailyNote note = note("note-with-history", END, originalObservations());
    when(notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        OWNER_ID, RECIPIENT_ID, END.minusDays(13), END)).thenReturn(List.of(note));
    Instant earlierAt = CREATED_AT.minusSeconds(60);
    Instant laterAt = CREATED_AT;
    NoteAddendum earlierAddendum = addendum(note, "earlier-addendum", earlierAt,
        new ObservationPresence(true, false, false, false, false, false, false, false), true,
        addendumObservations(Observation.state(ObservationState.NOT_RECORDED), "", VerificationState.UNVERIFIED));
    NoteAddendum laterAddendum = addendum(note, "later-addendum", laterAt,
        new ObservationPresence(false, false, false, false, false, false, false, true), false,
        addendumObservations(Observation.state(ObservationState.NOT_RECORDED), "later addendum text",
            VerificationState.NOT_ASSERTED));
    when(addenda.findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(List.of(note.getId()), OWNER_ID))
        .thenReturn(List.of(laterAddendum, earlierAddendum));

    NoteCorrectionEvent set = event(note, "event-set", 1, "appetite", CorrectionOperation.SET, "\"corrected appetite\"");
    NoteCorrectionEvent clear = event(note, "event-clear", 2, "sleep", CorrectionOperation.CLEAR, null);
    NoteCorrectionEvent unknown = event(note, "event-unknown", 3, "fallReported", CorrectionOperation.UNKNOWN, null);
    NoteCorrectionEvent notRecorded = event(note, "event-not-recorded", 4, "freeText", CorrectionOperation.NOT_RECORDED, null);
    when(correctionEvents.findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(
        List.of(note.getId()), OWNER_ID)).thenReturn(List.of(notRecorded, clear, set, unknown));

    SummaryNote summaryNote = loader.load(request(14)).notes().getFirst();
    String transcript = summaryNote.text();

    assertThat(transcript).contains(
        "ORIGINAL OBSERVATIONS",
        "mood [RECORDED]: original mood",
        "appetite [UNKNOWN]",
        "sleep [NOT_RECORDED]",
        "mobility [NOT_APPLICABLE]",
        "medicationStatus [LEGACY_AMBIGUOUS]: historical value withheld; meaning unresolved",
        "APPENDED ADDENDUM",
        "providedFields: [mood]",
        "providedFields: [freeText]",
        "medicationVerificationProvided: true",
        "medicationVerificationProvided: false",
        "medicationVerification: UNVERIFIED",
        "mood [NOT_RECORDED]",
        "freeText [RECORDED]: later addendum text",
        "STRUCTURED CORRECTION EVENT",
        "eventId: " + set.getId(),
        "noteId: " + note.getId(),
        "sequenceNo: 1",
        "createdAt: " + CREATED_AT,
        "actorId: " + OWNER_ID,
        "field: appetite",
        "operation: SET",
        "valueJson: \"corrected appetite\"",
        "operation: CLEAR",
        "operation: UNKNOWN",
        "operation: NOT_RECORDED");
    String earlierBlock = transcript.substring(transcript.indexOf("id: " + earlierAddendum.getId()),
        transcript.indexOf("id: " + laterAddendum.getId()));
    String laterBlock = transcript.substring(transcript.indexOf("id: " + laterAddendum.getId()),
        transcript.indexOf("eventId: " + set.getId()));
    assertThat(earlierBlock).contains("createdAt: " + earlierAt, "actorId: " + OWNER_ID,
        "providedFields: [mood]", "medicationVerificationProvided: true",
        "mood [NOT_RECORDED]", "appetite [UNKNOWN]", "medicationVerification: UNVERIFIED");
    assertThat(laterBlock).contains("createdAt: " + laterAt, "actorId: " + OWNER_ID,
        "providedFields: [freeText]", "medicationVerificationProvided: false",
        "freeText [RECORDED]: later addendum text", "medicationVerification: NOT_ASSERTED");
    assertThat(transcript.indexOf("ORIGINAL OBSERVATIONS"))
        .isLessThan(transcript.indexOf("id: " + earlierAddendum.getId()))
        .isLessThan(transcript.indexOf("eventId: " + set.getId()));
    assertThat(transcript.indexOf("id: " + earlierAddendum.getId()))
        .isLessThan(transcript.indexOf("id: " + laterAddendum.getId()));
    assertThat(transcript.indexOf("sequenceNo: 1"))
        .isLessThan(transcript.indexOf("sequenceNo: 2"))
        .isLessThan(transcript.indexOf("sequenceNo: 3"))
        .isLessThan(transcript.indexOf("sequenceNo: 4"));
    assertThat(transcript.substring(transcript.indexOf("eventId: " + clear.getId()),
        transcript.indexOf("eventId: " + unknown.getId()))).doesNotContain("valueJson:");
    assertThat(transcript.substring(transcript.indexOf("eventId: " + unknown.getId()),
        transcript.indexOf("eventId: " + notRecorded.getId()))).doesNotContain("valueJson:");
    assertThat(transcript.substring(transcript.indexOf("eventId: " + notRecorded.getId())))
        .doesNotContain("valueJson:");

    verify(addenda).findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(List.of(note.getId()), OWNER_ID);
    verify(correctionEvents).findAllByNote_IdInAndCaregiver_IdOrderByNote_IdAscSequenceNoAsc(
        List.of(note.getId()), OWNER_ID);
    verify(signals).forRange(RECIPIENT_ID, END.minusDays(13), END);
  }

  @Test
  @DisplayName("skips batched provenance queries when the date window has no notes")
  void skipsBatchedProvenanceQueriesWhenTheDateWindowHasNoNotes() {
    when(notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(
        OWNER_ID, RECIPIENT_ID, END.minusDays(29), END)).thenReturn(List.of());

    SummarySourceLoader.Source source = loader.load(request(30));

    assertThat(source.notes()).isEmpty();
    verifyNoInteractions(addenda, correctionEvents);
    verify(signals).forRange(RECIPIENT_ID, END.minusDays(29), END);
  }

  private SummaryRequest request(int days) {
    return new SummaryRequest(RECIPIENT_ID.toString(), END, days);
  }

  private DailyNote note(String name, LocalDate date, NoteObservations observations) {
    DailyNote note = new DailyNote(caregiver, recipient, date, observations, false, null);
    ReflectionTestUtils.setField(note, "id", id(name));
    return note;
  }

  private NoteAddendum addendum(DailyNote note, String name, Instant createdAt, ObservationPresence presence,
      boolean medicationVerificationProvided, NoteObservations observations) {
    NoteAddendum addendum = new NoteAddendum(note, caregiver, observations, presence,
        medicationVerificationProvided, null);
    ReflectionTestUtils.setField(addendum, "id", id(name));
    ReflectionTestUtils.setField(addendum, "createdAt", createdAt);
    return addendum;
  }

  private NoteCorrectionEvent event(DailyNote note, String name, long sequenceNo, String field,
      CorrectionOperation operation, String valueJson) {
    NoteCorrectionBatch batch = new NoteCorrectionBatch(note, caregiver, "batch-key", "request-hash");
    NoteCorrectionEvent event = new NoteCorrectionEvent(batch, note, caregiver, sequenceNo, field, operation, valueJson);
    ReflectionTestUtils.setField(event, "id", id(name));
    ReflectionTestUtils.setField(event, "createdAt", CREATED_AT);
    return event;
  }

  private static UUID id(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }

  private static NoteObservations originalObservations() {
    return new NoteObservations(
        Observation.recorded("original mood"),
        Observation.state(ObservationState.UNKNOWN),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_APPLICABLE),
        Observation.legacyAmbiguous("unresolved medication"),
        Observation.recorded(5),
        Observation.recorded(false),
        Observation.recorded("original note text"),
        VerificationState.VERIFIED);
  }

  private static NoteObservations addendumObservations(
      Observation<String> mood, String text, VerificationState verification) {
    return new NoteObservations(
        mood,
        Observation.state(ObservationState.UNKNOWN),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.recorded(text),
        verification);
  }
}
