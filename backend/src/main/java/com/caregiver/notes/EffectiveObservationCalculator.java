package com.caregiver.notes;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
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
    Map<String, EffectiveObservation> result = new LinkedHashMap<>();
    for (ObservationField field : ObservationField.values()) {
      Observation<?> initial = originalObservation(original, field);
      Object sourceValue = initial.state() == ObservationState.RECORDED ? initial.value() : null;
      JsonNode value = sourceValue == null ? null : mapper.valueToTree(sourceValue);
      result.put(field.wireName(), new EffectiveObservation(
          initial.state().name(), value, null, null, null, original.getCreatedAt()));
    }
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
      String state;
      switch (operation) {
        case SET -> { try { value = mapper.readTree(event.getValueJson()); } catch (Exception e) { throw new IllegalStateException("Stored correction value is invalid.", e); } state = "RECORDED"; }
        case CLEAR -> state = "NOT_RECORDED";
        case UNKNOWN -> state = "UNKNOWN";
        case NOT_RECORDED -> state = "NOT_RECORDED";
        default -> throw new IllegalStateException("Unsupported correction operation.");
      }
      EffectiveObservation current = new EffectiveObservation(state, value, operation.name(), event.getId(),
          event.getCaregiver().getId(), event.getCreatedAt());
      result.put(field.wireName(), current);
      changes.add(new EventChange(event.getId(), previous, current));
    }
    return new ReplayResult(Collections.unmodifiableMap(new LinkedHashMap<>(result)), List.copyOf(changes));
  }

  private Observation<?> originalObservation(DailyNote note, ObservationField field) {
    var observations = note.observations();
    return switch (field) {
      case MOOD -> observations.mood();
      case APPETITE -> observations.appetite();
      case SLEEP -> observations.sleep();
      case MOBILITY -> observations.mobility();
      case MEDICATION_STATUS -> observations.medicationStatus();
      case PAIN_SCORE -> observations.painScore();
      case FALL_REPORTED -> observations.fallReported();
      case FREE_TEXT -> observations.freeText();
    };
  }
}
