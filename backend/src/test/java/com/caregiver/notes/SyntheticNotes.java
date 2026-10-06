package com.caregiver.notes;

import com.caregiver.common.NoteSignals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only builders for synthetic 14-day note periods.
 * Index 0-6 is the previous 7-day window, index 7-13 the current window.
 * All dates are fixed relative to {@code end}; no randomness.
 */
public final class SyntheticNotes {

  private SyntheticNotes() {
  }

  private static NoteSignals clean(String recipientId, LocalDate date) {
    return new NoteSignals(recipientId, date, false, null, null, false, null, "");
  }

  /**
   * Fourteen clean days ending on {@code end} (inclusive).
   */
  public static List<NoteSignals> fourteenDays(String recipientId, LocalDate end) {
    List<NoteSignals> days = new ArrayList<>();
    for (int i = 13; i >= 0; i--) {
      days.add(clean(recipientId, end.minusDays(i)));
    }
    return List.copyOf(days);
  }

  /**
   * Clean previous week; current week has one fall with pain 8.
   */
  public static List<NoteSignals> fallAndPainWeek(String recipientId, LocalDate end) {
    List<NoteSignals> days = new ArrayList<>(fourteenDays(recipientId, end));
    days.set(12, new NoteSignals(recipientId, end.minusDays(1), true, 8, null, false, null, ""));
    return List.copyOf(days);
  }

  /**
   * Clean previous week; current week has two missed-med days. The second is
   * verified when {@code secondVerified} is true, unverified otherwise.
   */
  public static List<NoteSignals> medsWeek(String recipientId, LocalDate end, boolean secondVerified) {
    List<NoteSignals> days = new ArrayList<>(fourteenDays(recipientId, end));
    days.set(10, new NoteSignals(recipientId, end.minusDays(3), false, null, true, false, null, ""));
    days.set(12, new NoteSignals(
        recipientId, end.minusDays(1), false, null, true, !secondVerified, null, ""));
    return List.copyOf(days);
  }

  /**
   * Clean previous week; current week has three poor-appetite days.
   */
  public static List<NoteSignals> appetiteWeek(String recipientId, LocalDate end) {
    List<NoteSignals> days = new ArrayList<>(fourteenDays(recipientId, end));
    days.set(9, new NoteSignals(recipientId, end.minusDays(4), false, null, null, false, true, ""));
    days.set(11, new NoteSignals(recipientId, end.minusDays(2), false, null, null, false, true, ""));
    days.set(13, new NoteSignals(recipientId, end, false, null, null, false, true, ""));
    return List.copyOf(days);
  }
}
