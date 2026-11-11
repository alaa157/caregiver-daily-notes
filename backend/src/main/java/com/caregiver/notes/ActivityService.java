package com.caregiver.notes;

import com.caregiver.auth.AuthContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {
  public static final int DEFAULT_LIMIT = 10;
  public static final int MAX_LIMIT = 20;

  private static final Comparator<ActivityItemResponse> NEWEST_FIRST =
      Comparator.comparing(ActivityItemResponse::createdAt).reversed()
          .thenComparing(ActivityItemResponse::id, Comparator.reverseOrder())
          .thenComparing(item -> item.kind().name());

  private final DailyNoteRepository notes;
  private final NoteAddendumRepository addenda;
  private final NoteCorrectionEventRepository corrections;

  public ActivityService(DailyNoteRepository notes, NoteAddendumRepository addenda,
      NoteCorrectionEventRepository corrections) {
    this.notes = notes;
    this.addenda = addenda;
    this.corrections = corrections;
  }

  @Transactional(readOnly = true)
  public List<ActivityItemResponse> recent(int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ".");
    }
    UUID caregiverId = AuthContext.currentCaregiverId();
    PageRequest page = PageRequest.of(0, limit);
    List<ActivityItemResponse> items = new ArrayList<>(limit * 3);
    notes.findAllByCaregiver_IdOrderByCreatedAtDescIdDesc(caregiverId, page).forEach(note ->
        items.add(new ActivityItemResponse(note.getId(), ActivityItemResponse.ActivityKind.note,
            note.getRecipient().getId(), note.getCreatedAt())));
    addenda.findAllByCaregiver_IdOrderByCreatedAtDescIdDesc(caregiverId, page).forEach(addendum ->
        items.add(new ActivityItemResponse(addendum.getId(), ActivityItemResponse.ActivityKind.addendum,
            addendum.getNote().getRecipient().getId(), addendum.getCreatedAt())));
    corrections.findAllByCaregiver_IdOrderByCreatedAtDescIdDesc(caregiverId, page).forEach(event ->
        items.add(new ActivityItemResponse(event.getId(), ActivityItemResponse.ActivityKind.correction,
            event.getNote().getRecipient().getId(), event.getCreatedAt())));
    return items.stream().sorted(NEWEST_FIRST).limit(limit).toList();
  }
}
