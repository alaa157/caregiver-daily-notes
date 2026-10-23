package com.caregiver.notes;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.Collection; import java.util.List; import java.util.Optional; import java.util.UUID;
public interface NoteAddendumRepository extends JpaRepository<NoteAddendum,UUID>{
 List<NoteAddendum> findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAscIdAsc(UUID noteId,UUID owner);
 /** Single batched read backing the signals endpoint; avoids one query per note. */
 List<NoteAddendum> findAllByNote_IdInAndCaregiver_IdOrderByCreatedAtAscIdAsc(Collection<UUID> noteIds,UUID owner);
 Optional<NoteAddendum> findByCaregiver_IdAndIdempotencyKey(UUID owner,String key);
 long countByNote_IdAndCaregiver_Id(UUID noteId,UUID owner);
}