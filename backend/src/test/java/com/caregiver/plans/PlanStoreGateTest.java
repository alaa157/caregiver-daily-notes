package com.caregiver.plans;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanStoreGateTest {

  static SuggestedPlan plan() {
    return new SuggestedPlan(
        List.of(new SuggestedPlanChange(
            PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "3"), "check soon", List.of("n1"))),
        List.of(),
        List.of(),
        null);
  }

  @Test
  void fake_versionsSequentiallyAndCountsSaves() {
    var store = new InMemorySuggestedPlanStore();

    var v1 = store.save(plan());
    var v2 = store.save(plan());

    assertThat(v1.versionId()).isEqualTo("v1");
    assertThat(v2.versionId()).isEqualTo("v2");
    assertThat(store.saves()).isEqualTo(2);
    assertThat(store.saved()).hasSize(2);
  }

  @Test
  void fake_returnsDefensiveCopyOfSaved() {
    var store = new InMemorySuggestedPlanStore();
    store.save(plan());

    assertThatThrownBy(() -> store.saved().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
