package com.caregiver.preview;

import com.caregiver.auth.AuthContext;
import com.caregiver.auth.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Preview recipient CRUD scoped to the authenticated caregiver. Replaced by #8. */
@Service
public class RecipientService {

  private final RecipientRepository repository;

  public RecipientService(RecipientRepository repository) {
    this.repository = repository;
  }

  public List<RecipientDto> list() {
    return repository.findByCaregiverId(AuthContext.currentCaregiverId()).stream()
        .map(RecipientDto::of)
        .toList();
  }

  @Transactional
  public RecipientDto create(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new ValidationException("Name is required.");
    }
    Recipient saved = repository.save(new Recipient(AuthContext.currentCaregiverId(), name.trim(), true));
    return RecipientDto.of(saved);
  }

  public RecipientDto get(UUID id) {
    return RecipientDto.of(owned(id));
  }

  /** Shared ownership gate for the other preview services. */
  public Recipient requireOwned(UUID id) {
    return owned(id);
  }

  @Transactional
  public RecipientDto update(UUID id, boolean active) {
    Recipient recipient = owned(id);
    recipient.setActive(active);
    return RecipientDto.of(repository.save(recipient));
  }

  private Recipient owned(UUID id) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return repository
        .findByIdAndCaregiverId(id, caregiverId)
        .orElseGet(
            () -> {
              if (repository.existsById(id)) {
                throw new PreviewForbiddenException("Recipient belongs to another caregiver.");
              }
              throw new PreviewNotFoundException("Recipient not found.");
            });
  }
}
