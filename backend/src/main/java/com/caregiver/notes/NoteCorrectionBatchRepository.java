package com.caregiver.notes;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface NoteCorrectionBatchRepository extends Repository<NoteCorrectionBatch, UUID> {
  NoteCorrectionBatch save(NoteCorrectionBatch batch);
  Optional<NoteCorrectionBatch> findByCaregiver_IdAndIdempotencyKey(UUID caregiverId, String key);
}
