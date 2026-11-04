package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Single deterministic replay implementation. Inputs are never mutated. */
@Component
public class EffectiveObservationCalculator {
  public record EventChange(UUID eventId, EffectiveObservation previous, EffectiveObservation current) { }
  public record ReplayResult(Map<String, EffectiveObservation> effective, List<EventChange> changes) { }
  private final ObjectMapper mapper;
  public EffectiveObservationCalculator(ObjectMapper mapper) { this.mapper = mapper; }

  public Map<String, EffectiveObservation> replay(DailyNote original, List<NoteCorrectionEvent> events) {
    return replayWithHistory(original, events).effective();
  }

  public ReplayResult replayWithHistory(DailyNote original, List<NoteCorrectionEvent> events) {
    NoteObservations observations = original.observations();
    Map<String, EffectiveObservation> result = new LinkedHashMap<>();
    result.put("mood", fromOriginal(observations.mood(), original.getCreatedAt()));
    result.put("appetite", fromOriginal(observations.appetite(), original.getCreatedAt()));
    result.put("sleep", fromOriginal(observations.sleep(), original.getCreatedAt()));
    result.put("mobility", fromOriginal(observations.mobility(), original.getCreatedAt()));
    result.put("medicationStatus", fromOriginal(observations.medicationStatus(), original.getCreatedAt()));
    result.put("painScore", fromOriginal(observations.painScore(), original.getCreatedAt()));
    result.put("fallReported", fromOriginal(observations.fallReported(), original.getCreatedAt()));
    result.put("freeText", fromOriginal(observations.freeText(), original.getCreatedAt()));
    long previousSequence = 0;
    List<EventChange> changes = new ArrayList<>();
    for (NoteCorrectionEvent event : events) {
      if (!event.getNote().getId().equals(original.getId()) || event.getSequenceNo() <= previousSequence) {
        throw new IllegalArgumentException("Correction history is not in valid sequence order.");
      }
      previousSequence = event.getSequenceNo();
      ObservationField field = ObservationField.fromWire(event.getField());
      EffectiveObservation previous = result.get(field.wireName());
      CorrectionOperation operation = CorrectionOperation.valueOf(event.getOperation());
      JsonNode value = null;
      ObservationState state;
      switch (operation) {
        case SET -> {
          try { value = mapper.readTree(event.getValueJson()); }
          catch (Exception e) { throw new IllegalStateException("Stored correction value is invalid.", e); }
          field.validate(value);
          state = ObservationState.RECORDED;
        }
        case CLEAR, NOT_RECORDED -> state = ObservationState.NOT_RECORDED;
        case UNKNOWN -> state = ObservationState.UNKNOWN;
        default -> throw new IllegalStateException("Unsupported correction operation.");
      }
      EffectiveObservation current = new EffectiveObservation(state, value, null, operation,
          event.getId(), event.getCaregiver().getId(), event.getCreatedAt());
      result.put(field.wireName(), current);
      changes.add(new EventChange(event.getId(), previous, current));
    }
    return new ReplayResult(Collections.unmodifiableMap(new LinkedHashMap<>(result)), List.copyOf(changes));
  }

  private EffectiveObservation fromOriginal(Observation<?> observation, Instant createdAt) {
    return new EffectiveObservation(observation.state(), asJson(observation.value()), asJson(observation.rawValue()),
        null, null, null, createdAt);
  }

  private JsonNode asJson(Object value) {
    return value == null ? null : mapper.valueToTree(value);
  }
}
