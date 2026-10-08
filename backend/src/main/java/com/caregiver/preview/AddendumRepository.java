package com.caregiver.preview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddendumRepository extends JpaRepository<Addendum, UUID> {

  List<Addendum> findByNoteIdAndCaregiverIdOrderByCreatedAtAsc(UUID noteId, UUID caregiverId);
}
