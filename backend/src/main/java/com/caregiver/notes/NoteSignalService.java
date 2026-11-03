package com.caregiver.notes;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;

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
  public List<NoteSignals> forRange(UUID recipientId, LocalDate from, LocalDate to) {
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
        .map(note -> merge(note, grouped.getOrDefault(note.getId(), List.of())).toNoteSignals())
        .toList();
  }

  /**
   * Merges one note with its chronological addenda into a single signal.
   *
   * <p>Observation state, field presence, and medication verification are
   * independent. Addendum presence determines whether a field supersedes the
   * prior effective value; an explicit {@code NOT_RECORDED} is therefore
   * different from omission. Conflicting recorded medication reports produce
   * an effective {@code UNKNOWN} observation without rewriting history or
   * changing verification. Only an explicit {@code UNVERIFIED} verification
   * state sets the compatibility {@code medicationUnverified} projection.
   */
  private NoteSignalResponse merge(DailyNote note, List<NoteAddendum> addendaForNote) {
    NoteObservations original = note.observations();
    Observation<String> mood = original.mood();
    Observation<String> appetite = original.appetite();
    Observation<String> sleep = original.sleep();
    Observation<String> mobility = original.mobility();
    Observation<Boolean> fall = original.fallReported();
    Observation<Integer> pain = original.painScore();
    Observation<String> medication = original.medicationStatus();
    Observation<String> freeText = original.freeText();
    VerificationState medicationVerification = original.medicationVerification();

    // Presence is persisted separately so explicit NOT_RECORDED differs from omission.
    for (NoteAddendum addendum : addendaForNote) {
      NoteObservations correction = addendum.observations();
      ObservationPresence provided = addendum.observationPresence();
      if (provided.mood()) mood = correction.mood();
      if (provided.appetite()) appetite = correction.appetite();
      if (provided.sleep()) sleep = correction.sleep();
      if (provided.mobility()) mobility = correction.mobility();
      if (provided.fallReported()) fall = correction.fallReported();
      if (provided.painScore()) pain = correction.painScore();
      if (provided.medicationStatus()) {
        medication = correction.medicationStatus();
        if (!addendum.isMedicationVerificationProvided()) {
          medicationVerification = VerificationState.NOT_ASSERTED;
        }
      }
      if (provided.freeText()) freeText = correction.freeText();
      if (addendum.isMedicationVerificationProvided()) {
        medicationVerification = correction.medicationVerification();
      }
    }

    // Conflicting explicit reports are unknown; they do not alter verification.
    List<String> reportedStatuses = new ArrayList<>();
    addRecordedMedication(reportedStatuses, original.medicationStatus());
    for (NoteAddendum addendum : addendaForNote) {
      addRecordedMedication(reportedStatuses, addendum.observations().medicationStatus());
    }
    boolean contradictory = reportedStatuses.contains("TAKEN") && reportedStatuses.contains("MISSED");
    if (contradictory) {
      medication = Observation.state(ObservationState.UNKNOWN);
    }

    boolean medicationUnverified = medicationVerification == VerificationState.UNVERIFIED;
    Boolean missedMedication = null;
    if (medicationVerification == VerificationState.VERIFIED
        && medication.state() == ObservationState.RECORDED) {
      if ("MISSED".equals(medication.value())) missedMedication = true;
      else if ("TAKEN".equals(medication.value())) missedMedication = false;
    }

    NoteObservations effective = new NoteObservations(
        mood, appetite, sleep, mobility, medication, pain, fall, freeText, medicationVerification);
    return new NoteSignalResponse(
        note.getRecipient().getId(),
        note.getDate(),
        recorded(mood),
        recorded(appetite),
        recorded(sleep),
        recorded(mobility),
        medication.state() == ObservationState.UNKNOWN ? "UNKNOWN" : recorded(medication),
        recorded(fall),
        recorded(pain),
        missedMedication,
        medicationUnverified,
        appetite.state() == ObservationState.RECORDED ? poor(appetite.value()) : null,
        recorded(freeText),
        effective);
  }

  private void addRecordedMedication(List<String> values, Observation<String> observation) {
    if (observation.state() == ObservationState.RECORDED
        && ("TAKEN".equals(observation.value()) || "MISSED".equals(observation.value()))) {
      values.add(observation.value());
    }
  }

  private <T> T recorded(Observation<T> observation) {
    return observation.state() == ObservationState.RECORDED ? observation.value() : null;
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
