package com.caregiver.notes;
import jakarta.persistence.LockModeType; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.time.LocalDate; import java.util.Collection; import java.util.List; import java.util.Optional; import java.util.UUID;
public interface DailyNoteRepository extends JpaRepository<DailyNote,UUID>{
 Optional<DailyNote> findByIdAndCaregiver_Id(UUID id,UUID caregiverId); boolean existsByIdAndCaregiver_Id(UUID id,UUID caregiverId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from DailyNote n where n.id = :id and n.caregiver.id = :owner") Optional<DailyNote> findOwnedForUpdate(@Param("id") UUID id,@Param("owner") UUID owner);
 Optional<DailyNote> findByRecipient_IdAndCaregiver_IdAndDate(UUID recipientId,UUID caregiverId,LocalDate date);
 Optional<DailyNote> findByCaregiver_IdAndIdempotencyKey(UUID caregiverId,String key);
 Page<DailyNote> findAllByCaregiver_IdAndDateBetween(UUID owner,LocalDate from,LocalDate to,Pageable p);
 List<DailyNote> findAllByCaregiver_IdOrderByCreatedAtDescIdDesc(UUID owner,Pageable p);
 Page<DailyNote> findAllByCaregiver_IdAndRecipient_IdAndDateBetween(UUID owner,UUID recipient,LocalDate from,LocalDate to,Pageable p);
 List<DailyNote> findAllByCaregiver_IdAndRecipient_IdAndDateBetweenOrderByDateAsc(UUID owner,UUID recipient,LocalDate from,LocalDate to);
 /** Single aggregate query backing history addendum counts; avoids one count query per row. */
 @Query("select a.note.id, count(a) from NoteAddendum a where a.note.id in :noteIds and a.caregiver.id = :owner group by a.note.id")
 List<Object[]> countAddendaByNoteIds(@Param("noteIds") Collection<UUID> noteIds, @Param("owner") UUID owner);
}
