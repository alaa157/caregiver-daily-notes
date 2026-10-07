package com.caregiver.plans;

import java.util.ArrayList;
import java.util.List;

/**
 * Test fake for {@link SuggestedPlanStore}. Assigns sequential version ids
 * ({@code v1}, {@code v2}, …) and records every save for gate assertions.
 */
public class InMemorySuggestedPlanStore implements SuggestedPlanStore {

  private final List<SuggestedPlan> saved = new ArrayList<>();

  @Override
  public StoredPlanVersion save(SuggestedPlan plan) {
    saved.add(plan);
    return new StoredPlanVersion("v" + saved.size(), plan);
  }

  public int saves() {
    return saved.size();
  }

  public List<SuggestedPlan> saved() {
    return List.copyOf(saved);
  }
}
