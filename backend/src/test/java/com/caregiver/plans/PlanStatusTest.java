package com.caregiver.plans;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanStatusTest {
  @Test
  void suggestedPlanCanBeAcceptedOrDismissedButAcceptedPlanOnlyArchives() {
    CarePlan accepted = new CarePlan(null, 1, PlanStatus.SUGGESTED, "content", null, null, null);
    accepted.changeStatus(PlanStatus.ACCEPTED);
    assertThat(accepted.getStatus()).isEqualTo(PlanStatus.ACCEPTED);
    accepted.changeStatus(PlanStatus.ARCHIVED);
    assertThat(accepted.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    assertThatThrownBy(() -> accepted.changeStatus(PlanStatus.ACCEPTED))
        .isInstanceOf(IllegalPlanTransitionException.class);
  }

  @Test
  void dismissedPlanCannotBeReactivated() {
    CarePlan dismissed = new CarePlan(null, 1, PlanStatus.SUGGESTED, "content", null, null, null);
    dismissed.changeStatus(PlanStatus.DISMISSED);
    assertThatThrownBy(() -> dismissed.changeStatus(PlanStatus.ACCEPTED))
        .isInstanceOf(IllegalPlanTransitionException.class);
  }

  @Test
  void activeAcceptedMarkerTracksStateTransitions() throws Exception {
    var field = CarePlan.class.getDeclaredField("activeAcceptedKey");
    field.setAccessible(true);
    CarePlan suggested = new CarePlan(null, 1, PlanStatus.SUGGESTED, "content", null, null, null);
    assertThat(field.get(suggested)).isNull();
    suggested.changeStatus(PlanStatus.ACCEPTED);
    assertThat(field.get(suggested)).isEqualTo(Boolean.TRUE);
    suggested.changeStatus(PlanStatus.ARCHIVED);
    assertThat(field.get(suggested)).isNull();
  }
}
