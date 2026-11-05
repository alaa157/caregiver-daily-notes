package com.caregiver.plans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PlanDocumentCodec {
  private final ObjectMapper mapper;
  public PlanDocumentCodec(ObjectMapper mapper) { this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); }
  public PlanDocument read(CarePlan plan) {
    boolean caregiverSubmitted = plan.getSourceType() == PlanSource.CAREGIVER_SUBMITTED;
    if (!caregiverSubmitted && plan.getSourceType() != PlanSource.AI_GENERATED
        && plan.getSourceType() != PlanSource.CAREGIVER_EDITED) return null;
    try { return mapper.readValue(plan.getContent(), PlanDocument.class); }
    catch (Exception invalidStoredDocument) {
      if (caregiverSubmitted) return null;
      throw new ProtectedPlanException();
    }
  }
  public String write(PlanDocument document) {
    try {
      String value = mapper.writeValueAsString(document);
      if (value.length() > 20_000) throw new ProtectedPlanException();
      return value;
    } catch (Exception invalidDocument) { throw new ProtectedPlanException(); }
  }
}
