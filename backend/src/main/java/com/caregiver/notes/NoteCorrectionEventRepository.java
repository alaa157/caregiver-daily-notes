package com.caregiver.notes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

public interface NoteCorrectionEventRepository extends Repository<NoteCorrectionEvent, UUID> {
  NoteCorrectionEvent save(NoteCorrectionEvent event);
  List<NoteCorrectionEvent> findAllByNote_IdAndCaregiver_IdOrderBySequenceNoAsc(UUID noteId, UUID caregiverId);
  Optional<NoteCorrectionEvent> findFirstByNote_IdAndCaregiver_IdOrderBySequenceNoDesc(UUID noteId, UUID caregiverId);
  long countByNote_IdAndCaregiver_Id(UUID noteId, UUID caregiverId);
  @Query("select e.note.id, count(e) from NoteCorrectionEvent e where e.note.id in :noteIds and e.caregiver.id = :owner group by e.note.id")
  List<Object[]> countByNoteIds(@Param("noteIds") Collection<UUID> noteIds, @Param("owner") UUID owner);
}
