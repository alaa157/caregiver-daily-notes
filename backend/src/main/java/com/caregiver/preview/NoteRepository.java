package com.caregiver.preview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

  Optional<Note> findByIdAndCaregiverId(UUID id, UUID caregiverId);

  List<Note> findByCaregiverIdAndRecipientIdOrderByDateDesc(UUID caregiverId, UUID recipientId);
}
