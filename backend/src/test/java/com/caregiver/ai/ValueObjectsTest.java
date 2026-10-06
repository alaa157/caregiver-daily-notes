package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

  @Test
  void noteSignals_rejectsOutOfRangePain() {
    assertThatThrownBy(() -> new NoteSignals("r1", LocalDate.now(), false, 11, null, false, null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void noteSignals_acceptsNullsAsMissing() {
    var n = new NoteSignals("r1", LocalDate.of(2026, 10, 1), false, null, null, false, null, "irrelevant text");
    assertThat(n.painScore()).isNull();
  }
}
