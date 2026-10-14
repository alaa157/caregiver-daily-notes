package com.caregiver.notes;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional; import java.util.UUID;
public interface NoteAddendumRepository extends JpaRepository<NoteAddendum,UUID>{
 List<NoteAddendum> findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAsc(UUID noteId,UUID owner);
 Optional<NoteAddendum> findByCaregiver_IdAndIdempotencyKey(UUID owner,String key);
 long countByNote_IdAndCaregiver_Id(UUID noteId,UUID owner);
}