package com.caregiver.plans;

import com.caregiver.ai.InvalidModelOutputException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hard lock on medication sections: the proposed list must contain exactly
 * the same entries as the current list, compared as an order-insensitive
 * multiset of exact strings. Any addition, removal, or field change throws —
 * checked before anything persists.
 */
public final class MedicationHardLock {

  private MedicationHardLock() {
  }

  public static void checkUnchanged(List<MedicationEntry> current, List<MedicationEntry> proposed) {
    Map<MedicationEntry, Integer> counts = new HashMap<>();
    if (current != null) {
      for (MedicationEntry entry : current) {
        counts.merge(entry, 1, Integer::sum);
      }
    }
    if (proposed != null) {
      for (MedicationEntry entry : proposed) {
        counts.merge(entry, -1, Integer::sum);
      }
    }
    List<String> diff = new ArrayList<>();
    for (Map.Entry<MedicationEntry, Integer> e : counts.entrySet()) {
      if (e.getValue() != 0) {
        MedicationEntry m = e.getKey();
        diff.add((e.getValue() > 0 ? "removed " : "added ") + m.name() + "|" + m.dose() + "|" + m.schedule());
      }
    }
    if (!diff.isEmpty()) {
      diff.sort(String::compareTo);
      throw new InvalidModelOutputException("Medication section changed: " + String.join(", ", diff));
    }
  }
}
