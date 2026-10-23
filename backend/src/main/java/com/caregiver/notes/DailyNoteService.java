package com.caregiver.notes;

import com.caregiver.audit.AuditService;
import com.caregiver.auth.AuthContext;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.RecipientNotFoundException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caregiver-scoped daily notes with race-safe idempotent writes.
 *
 * <p>Ownership is always derived from the authenticated principal; request
 * bodies never carry a caregiver id. Cross-caregiver access resolves to
 * {@code 404} through owner-scoped queries so one caregiver's data is never
 * distinguishable from missing data.
 *
 * <p>Idempotency is enforced by the database unique constraints
 * ({@code uk_daily_note_owner_idempotency},
 * {@code uk_note_addenda_owner_idempotency}) rather than by the
 * check-then-insert fast path alone. Concurrent requests with the same key
 * therefore serialize on the constraint: the winner commits, and every loser
 * recovers by reading the winner's committed row in a fresh transaction and
 * either returning it (identical payload) or rejecting the reuse with
 * {@link IdempotencyConflictException} (different payload). The write itself
 * runs in its own transaction so a constraint violation can never poison the
 * recovery read. Unattributed integrity violations are rethrown untouched.
 *
 * <p>Replaying a request with the same key and payload returns the original
 * result with the normal {@code 201} status; clients must compare bodies, not
 * statuses, to detect a replay.
 *
 * <p>Every successful write records an audit event in the same transaction
 * (via {@link AuditService}, which joins the caller transaction), carrying
 * only identifiers — never free-text note contents.
 */
@Service
public class DailyNoteService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final DailyNoteRepository notes;
  private final NoteAddendumRepository addenda;
  private final CareRecipientRepository recipients;
  private final CaregiverRepository caregivers;
  private final AuditService audit;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public DailyNoteService(DailyNoteRepository notes,
      NoteAddendumRepository addenda,
      CareRecipientRepository recipients,
      CaregiverRepository caregivers,
      AuditService audit,
      PlatformTransactionManager transactionManager) {
    this.notes = notes;
    this.addenda = addenda;
    this.recipients = recipients;
    this.caregivers = caregivers;
    this.audit = audit;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  public NoteResponse create(NoteRequest request, String key) {
    UUID owner = AuthContext.currentCaregiverId();
    String idempotencyKey = normalizeKey(key);
    if (idempotencyKey != null) {
      NoteResponse replay = readTx.execute(status -> notes
          .findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey)
          .map(existing -> {
            if (!sameRequest(existing, request)) {
              throw new IdempotencyConflictException();
            }
            return response(existing);
          })
          .orElse(null));
      if (replay != null) {
        return replay;
      }
    }
    try {
      return writeTx.execute(status -> {
        if (idempotencyKey != null) {
          var existing = notes.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
          if (existing.isPresent()) {
            if (!sameRequest(existing.get(), request)) {
              throw new IdempotencyConflictException();
            }
            return response(existing.get());
          }
        }
        var recipient = recipients.findByIdAndCaregiver_Id(request.recipientId(), owner)
            .orElseThrow(RecipientNotFoundException::new);
        if (!recipient.isActive()) {
          throw new IllegalArgumentException("Recipient is inactive.");
        }
        if (!caregivers.existsById(owner)) {
          throw new RecipientNotFoundException();
        }
        var saved = notes.saveAndFlush(new DailyNote(
            caregivers.getReferenceById(owner),
            recipients.getReferenceById(request.recipientId()),
            request.date(), request.mood(), request.appetite(), request.sleep(), request.mobility(),
            request.medicationStatus(), request.medicationVerified(), request.painScore(),
            request.fallReported(), request.freeText(), idempotencyKey));
        audit.record("NOTE_CREATED", "DAILY_NOTE", saved.getId().toString(),
            "recipientId=" + request.recipientId() + ";date=" + request.date());
        return response(saved);
      });
    } catch (DataIntegrityViolationException e) {
      return recoverCreate(owner, idempotencyKey, request, e);
    }
  }

  /**
   * Resolves a write that lost the unique-constraint race. Runs in a fresh
   * read transaction so it always observes the winner's committed row.
   */
  private NoteResponse recoverCreate(UUID owner, String idempotencyKey, NoteRequest request,
      DataIntegrityViolationException failure) {
    return readTx.execute(status -> {
      if (idempotencyKey != null) {
        var existing = notes.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
        if (existing.isPresent()) {
          if (!sameRequest(existing.get(), request)) {
            throw new IdempotencyConflictException();
          }
          return response(existing.get());
        }
      }
      if (notes.findByRecipient_IdAndCaregiver_IdAndDate(request.recipientId(), owner, request.date())
          .isPresent()) {
        throw new NoteConflictException();
      }
      throw failure;
    });
  }

  public NoteAddendumResponse append(UUID noteId, AddendumRequest request, String key) {
    UUID owner = AuthContext.currentCaregiverId();
    String idempotencyKey = normalizeKey(key);
    requireAddendumContent(request);
    if (idempotencyKey != null) {
      NoteAddendumResponse replay = readTx.execute(status -> addenda
          .findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey)
          .map(existing -> {
            if (!existing.getNote().getId().equals(noteId) || !sameRequest(existing, request)) {
              throw new IdempotencyConflictException();
            }
            return NoteAddendumResponse.from(existing);
          })
          .orElse(null));
      if (replay != null) {
        return replay;
      }
    }
    try {
      return writeTx.execute(status -> {
        if (idempotencyKey != null) {
          var existing = addenda.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
          if (existing.isPresent()) {
            if (!existing.get().getNote().getId().equals(noteId)
                || !sameRequest(existing.get(), request)) {
              throw new IdempotencyConflictException();
            }
            return NoteAddendumResponse.from(existing.get());
          }
        }
        var note = notes.findByIdAndCaregiver_Id(noteId, owner)
            .orElseThrow(NoteNotFoundException::new);
        if (!caregivers.existsById(owner)) {
          throw new NoteNotFoundException();
        }
        var saved = addenda.saveAndFlush(new NoteAddendum(
            note, caregivers.getReferenceById(owner),
            request.mood(), request.appetite(), request.sleep(), request.mobility(),
            request.medicationStatus(), request.medicationVerified(), request.painScore(),
            request.fallReported(), request.freeText(), idempotencyKey));
        audit.record("NOTE_ADDENDUM_CREATED", "NOTE_ADDENDUM", saved.getId().toString(),
            "noteId=" + noteId);
        return NoteAddendumResponse.from(saved);
      });
    } catch (DataIntegrityViolationException e) {
      return recoverAppend(owner, noteId, idempotencyKey, request, e);
    }
  }

  /** Resolves an addendum write that lost the idempotency race. */
  private NoteAddendumResponse recoverAppend(UUID owner, UUID noteId, String idempotencyKey,
      AddendumRequest request, DataIntegrityViolationException failure) {
    return readTx.execute(status -> {
      if (idempotencyKey != null) {
        var existing = addenda.findByCaregiver_IdAndIdempotencyKey(owner, idempotencyKey);
        if (existing.isPresent()) {
          if (!existing.get().getNote().getId().equals(noteId)
              || !sameRequest(existing.get(), request)) {
            throw new IdempotencyConflictException();
          }
          return NoteAddendumResponse.from(existing.get());
        }
      }
      throw failure;
    });
  }

  @Transactional(readOnly = true)
  public NoteDetailResponse getDetail(UUID id) {
    UUID owner = AuthContext.currentCaregiverId();
    DailyNote note = notes.findByIdAndCaregiver_Id(id, owner)
        .orElseThrow(NoteNotFoundException::new);
    List<NoteAddendumResponse> ordered = addenda
        .findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAscIdAsc(id, owner)
        .stream().map(NoteAddendumResponse::from).toList();
    return NoteDetailResponse.from(note, ordered);
  }

  @Transactional(readOnly = true)
  public NoteDetailResponse getByRecipientDateDetail(UUID recipientId, LocalDate date) {
    UUID owner = AuthContext.currentCaregiverId();
    DailyNote note = notes.findByRecipient_IdAndCaregiver_IdAndDate(recipientId, owner, date)
        .orElseThrow(NoteNotFoundException::new);
    List<NoteAddendumResponse> ordered = addenda
        .findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAscIdAsc(note.getId(), owner)
        .stream().map(NoteAddendumResponse::from).toList();
    return NoteDetailResponse.from(note, ordered);
  }

  @Transactional(readOnly = true)
  public NoteResponse get(UUID id) {
    UUID owner = AuthContext.currentCaregiverId();
    return notes.findByIdAndCaregiver_Id(id, owner).map(this::response)
        .orElseThrow(NoteNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public NoteResponse getByRecipientDate(UUID recipientId, LocalDate date) {
    UUID owner = AuthContext.currentCaregiverId();
    return notes.findByRecipient_IdAndCaregiver_IdAndDate(recipientId, owner, date)
        .map(this::response).orElseThrow(NoteNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<NoteResponse> history(UUID recipientId, LocalDate from, LocalDate to, int page, int size) {
    UUID owner = AuthContext.currentCaregiverId();
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to are required.");
    }
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from must be on or before to.");
    }
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("page must be non-negative and size must be 1-100.");
    }
    var pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "id")));
    Page<DailyNote> rows = recipientId == null
        ? notes.findAllByCaregiver_IdAndDateBetween(owner, from, to, pageable)
        : notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetween(owner, recipientId, from, to, pageable);
    // One aggregate query for the whole page instead of one count per row.
    Map<UUID, Long> counts = new HashMap<>();
    List<UUID> ids = rows.map(DailyNote::getId).toList();
    if (!ids.isEmpty()) {
      for (Object[] row : notes.countAddendaByNoteIds(ids, owner)) {
        counts.put((UUID) row[0], (Long) row[1]);
      }
    }
    return rows.map(note -> response(note, counts.getOrDefault(note.getId(), 0L)));
  }

  @Transactional(readOnly = true)
  public List<NoteAddendumResponse> addenda(UUID noteId) {
    UUID owner = AuthContext.currentCaregiverId();
    if (!notes.existsByIdAndCaregiver_Id(noteId, owner)) {
      throw new NoteNotFoundException();
    }
    return addenda.findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAscIdAsc(noteId, owner)
        .stream().map(NoteAddendumResponse::from).toList();
  }

  private boolean sameRequest(DailyNote note, NoteRequest request) {
    return Objects.equals(note.getRecipient().getId(), request.recipientId())
        && Objects.equals(note.getDate(), request.date())
        && Objects.equals(note.getMood(), request.mood())
        && Objects.equals(note.getAppetite(), request.appetite())
        && Objects.equals(note.getSleep(), request.sleep())
        && Objects.equals(note.getMobility(), request.mobility())
        && Objects.equals(note.getMedicationStatus(), request.medicationStatus())
        && Objects.equals(note.getMedicationVerified(), request.medicationVerified())
        && Objects.equals(note.getPainScore(), request.painScore())
        && Objects.equals(note.getFallReported(), request.fallReported())
        && Objects.equals(note.getFreeText(), request.freeText());
  }

  private boolean sameRequest(NoteAddendum addendum, AddendumRequest request) {
    return Objects.equals(addendum.getMood(), request.mood())
        && Objects.equals(addendum.getAppetite(), request.appetite())
        && Objects.equals(addendum.getSleep(), request.sleep())
        && Objects.equals(addendum.getMobility(), request.mobility())
        && Objects.equals(addendum.getMedicationStatus(), request.medicationStatus())
        && Objects.equals(addendum.getMedicationVerified(), request.medicationVerified())
        && Objects.equals(addendum.getPainScore(), request.painScore())
        && Objects.equals(addendum.getFallReported(), request.fallReported())
        && Objects.equals(addendum.getFreeText(), request.freeText());
  }

  private NoteResponse response(DailyNote note) {
    return response(note, addenda.countByNote_IdAndCaregiver_Id(note.getId(), note.getCaregiver().getId()));
  }

  private NoteResponse response(DailyNote note, long count) {
    return NoteResponse.from(note, count);
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new IllegalArgumentException("Idempotency-Key must be 1-100 characters.");
    }
    return key;
  }

  private void requireAddendumContent(AddendumRequest request) {
    if (request.mood() == null && request.appetite() == null && request.sleep() == null
        && request.mobility() == null && request.medicationStatus() == null
        && request.medicationVerified() == null && request.painScore() == null
        && request.fallReported() == null && request.freeText() == null) {
      throw new IllegalArgumentException("Addendum must correct at least one field.");
    }
  }
}
