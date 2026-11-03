package com.caregiver.notes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ObservationPresence(
    boolean mood,
    boolean appetite,
    boolean sleep,
    boolean mobility,
    boolean medicationStatus,
    boolean painScore,
    boolean fallReported,
    boolean freeText) {

  public boolean any() {
    return mood || appetite || sleep || mobility || medicationStatus
        || painScore || fallReported || freeText;
  }

  public List<String> fieldNames() {
    Set<String> fields = new LinkedHashSet<>();
    if (mood) fields.add("mood");
    if (appetite) fields.add("appetite");
    if (sleep) fields.add("sleep");
    if (mobility) fields.add("mobility");
    if (medicationStatus) fields.add("medicationStatus");
    if (painScore) fields.add("painScore");
    if (fallReported) fields.add("fallReported");
    if (freeText) fields.add("freeText");
    return List.copyOf(fields);
  }
}
