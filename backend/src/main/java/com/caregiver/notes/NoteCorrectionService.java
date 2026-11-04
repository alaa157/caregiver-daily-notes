package com.caregiver.notes;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.auth.CaregiverRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Append-only, caregiver-scoped typed note corrections with batch idempotency. */
@Service
public class NoteCorrectionService {
  private final DailyNoteRepository notes;
  private final NoteCorrectionBatchRepository batches;
  private final NoteCorrectionEventRepository events;
  private final NoteAddendumRepository addenda;
  private final CaregiverRepository caregivers;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final EffectiveObservationCalculator calculator;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public NoteCorrectionService(DailyNoteRepository notes, NoteCorrectionBatchRepository batches,
      NoteCorrectionEventRepository events, NoteAddendumRepository addenda, CaregiverRepository caregivers, AuditService audit,
      ObjectMapper mapper, EffectiveObservationCalculator calculator,
      PlatformTransactionManager transactionManager) {
    this.notes = notes; this.batches = batches; this.events = events; this.addenda = addenda; this.caregivers = caregivers;
    this.audit = audit; this.mapper = mapper; this.calculator = calculator;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  public CorrectionHistoryResponse append(UUID noteId, CorrectionRequest request, String suppliedKey) {
    validate(request);
    UUID owner = AuthContext.currentCaregiverId();
    String key = normalizeKey(suppliedKey);
    String hash = digest(noteId, request);
    if (key != null) {
      CorrectionHistoryResponse replay = readTx.execute(status -> batches
          .findByCaregiver_IdAndIdempotencyKey(owner, key)
          .map(batch -> replayOrConflict(batch, noteId, hash, owner)).orElse(null));
      if (replay != null) return replay;
    }
    try {
      return writeTx.execute(status -> {
        if (key != null) {
          var previous = batches.findByCaregiver_IdAndIdempotencyKey(owner, key);
          if (previous.isPresent()) return replayOrConflict(previous.get(), noteId, hash, owner);
        }
        DailyNote note = notes.findOwnedForUpdate(noteId, owner).orElseThrow(NoteNotFoundException::new);
        var caregiver = caregivers.findById(owner).orElseThrow(NoteNotFoundException::new);
        NoteCorrectionBatch batch = batches.save(new NoteCorrectionBatch(note, caregiver, key, hash));
        long sequence = events.findFirstByNote_IdAndCaregiver_IdOrderBySequenceNoDesc(noteId, owner)
            .map(NoteCorrectionEvent::getSequenceNo).orElse(0L);
        for (FieldCorrectionRequest correction : request.corrections()) {
          ObservationField field = ObservationField.fromWire(correction.field());
          String valueJson = correction.operation() == CorrectionOperation.SET ? correction.value().toString() : null;
          events.save(new NoteCorrectionEvent(batch, note, caregiver, ++sequence,
              field.wireName(), correction.operation(), valueJson));
        }
        audit.record("NOTE_CORRECTION_CREATED", "NOTE_CORRECTION_BATCH", batch.getId().toString(),
            "noteId=" + noteId + ";eventCount=" + request.corrections().size());
        return response(note, owner);
      });
    } catch (DataIntegrityViolationException failure) {
      if (key == null) throw failure;
      return readTx.execute(status -> batches.findByCaregiver_IdAndIdempotencyKey(owner, key)
          .map(batch -> replayOrConflict(batch, noteId, hash, owner)).orElseThrow(() -> failure));
    }
  }

  public CorrectionHistoryResponse history(UUID noteId) {
    UUID owner = AuthContext.currentCaregiverId();
    return readTx.execute(status -> {
      DailyNote note = notes.findByIdAndCaregiver_Id(noteId, owner).orElseThrow(NoteNotFoundException::new);
      return response(note, owner);
    });
  }

  private CorrectionHistoryResponse replayOrConflict(NoteCorrectionBatch batch, UUID noteId, String hash, UUID owner) {
    if (!batch.getNote().getId().equals(noteId) || !MessageDigest.isEqual(
        batch.getRequestHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII))) {
      throw new IdempotencyConflictException();
    }
    DailyNote note = notes.findByIdAndCaregiver_Id(noteId, owner).orElseThrow(NoteNotFoundException::new);
    return response(note, owner);
  }

  private CorrectionHistoryResponse response(DailyNote note, UUID owner) {
    List<NoteCorrectionEvent> ordered = events
        .findAllByNote_IdAndCaregiver_IdOrderBySequenceNoAsc(note.getId(), owner);
    EffectiveObservationCalculator.ReplayResult replay = calculator.replayWithHistory(note, ordered);
    Map<UUID, EffectiveObservationCalculator.EventChange> changes = replay.changes().stream()
        .collect(java.util.stream.Collectors.toMap(EffectiveObservationCalculator.EventChange::eventId, change -> change));
    List<NoteCorrectionEventResponse> eventResponses = ordered.stream().map(event -> {
      JsonNode value = null;
      if (event.getValueJson() != null) {
        try { value = mapper.readTree(event.getValueJson()); }
        catch (Exception e) { throw new IllegalStateException("Stored correction value is invalid.", e); }
      }
      EffectiveObservationCalculator.EventChange change = changes.get(event.getId());
      return NoteCorrectionEventResponse.from(event, value, change.previous(), change.current());
    }).toList();
    long addendumCount = addenda.countByNote_IdAndCaregiver_Id(note.getId(), owner);
    return new CorrectionHistoryResponse(NoteResponse.from(note, addendumCount, ordered.size()), eventResponses,
        replay.effective());
  }

  private void validate(CorrectionRequest request) {
    if (request == null || request.corrections() == null || request.corrections().isEmpty()) {
      throw new IllegalArgumentException("At least one correction is required.");
    }
    for (FieldCorrectionRequest correction : request.corrections()) {
      if (correction == null || correction.field() == null || correction.operation() == null) {
        throw new IllegalArgumentException("Each correction requires a field and operation.");
      }
      ObservationField field = ObservationField.fromWire(correction.field());
      if (correction.operation() == CorrectionOperation.SET) field.validate(correction.value());
      else if (correction.value() != null) throw new IllegalArgumentException("Only SET may include a value.");
    }
  }

  private String digest(UUID noteId, CorrectionRequest request) {
    try {
      // Hash the structured request so arbitrary text values cannot collide with our separators.
      byte[] canonicalRequest = mapper.writeValueAsBytes(List.of(noteId.toString(), request.corrections()));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalRequest));
    }
    catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable.", e); }
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) return null;
    if (key.length() > 100) throw new IllegalArgumentException("Idempotency-Key must be 1-100 characters.");
    return key;
  }
}
