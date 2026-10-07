package com.caregiver.plans;

import com.caregiver.ai.InvalidModelOutputException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class MedicationHardLockTest {

  static MedicationEntry med(String name, String dose, String schedule) {
    return new MedicationEntry(name, dose, schedule);
  }

  @Test
  void identicalSections_pass() {
    var current = List.of(med("Aspirin", "5mg", "daily"), med("Vitamin D", "1000IU", "daily"));
    var proposed = List.of(med("Aspirin", "5mg", "daily"), med("Vitamin D", "1000IU", "daily"));

    assertThatNoException().isThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed));
  }

  @Test
  void reorderedSections_pass() {
    var current = List.of(med("Aspirin", "5mg", "daily"), med("Vitamin D", "1000IU", "daily"));
    var proposed = List.of(med("Vitamin D", "1000IU", "daily"), med("Aspirin", "5mg", "daily"));

    assertThatNoException().isThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed));
  }

  @Test
  void addedMedication_rejected() {
    var current = List.of(med("Aspirin", "5mg", "daily"));
    var proposed = List.of(med("Aspirin", "5mg", "daily"), med("Ibuprofen", "200mg", "as needed"));

    assertThatThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("Ibuprofen");
  }

  @Test
  void removedMedication_rejected() {
    var current = List.of(med("Aspirin", "5mg", "daily"), med("Vitamin D", "1000IU", "daily"));
    var proposed = List.of(med("Aspirin", "5mg", "daily"));

    assertThatThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("Vitamin D");
  }

  @Test
  void renamedMedication_rejected() {
    var current = List.of(med("Aspirin", "5mg", "daily"));
    var proposed = List.of(med("Aspirine", "5mg", "daily"));

    assertThatThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void changedDose_rejected() {
    var current = List.of(med("Aspirin", "5mg", "daily"));
    var proposed = List.of(med("Aspirin", "10mg", "daily"));

    assertThatThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed))
        .isInstanceOf(InvalidModelOutputException.class)
        .hasMessageContaining("10mg");
  }

  @Test
  void changedSchedule_rejected() {
    var current = List.of(med("Aspirin", "5mg", "daily"));
    var proposed = List.of(med("Aspirin", "5mg", "twice daily"));

    assertThatThrownBy(() -> MedicationHardLock.checkUnchanged(current, proposed))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void nullLists_treatedAsEmpty() {
    assertThatNoException().isThrownBy(() -> MedicationHardLock.checkUnchanged(null, null));
    assertThatNoException().isThrownBy(() -> MedicationHardLock.checkUnchanged(List.of(), null));
  }
}
