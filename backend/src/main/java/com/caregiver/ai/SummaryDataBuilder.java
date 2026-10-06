package com.caregiver.ai;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders in-window notes as delimited untrusted data for the model. Notes
 * outside {@code [end-days+1, end]} are excluded; the rest sort by date,
 * then id, so identical input always renders identically.
 */
public final class SummaryDataBuilder {

  private SummaryDataBuilder() {
  }

  public static String build(List<SummaryNote> notes, LocalDate end, int days) {
    if (notes == null || notes.isEmpty() || end == null || days <= 0) {
      return "";
    }
    LocalDate start = end.minusDays((long) days - 1);
    return notes.stream()
        .filter(n -> n != null && n.date() != null
            && !n.date().isBefore(start)
            && !n.date().isAfter(end))
        .sorted(Comparator.comparing(SummaryNote::date).thenComparing(SummaryNote::noteId))
        .map(n -> "[NOTE id=\"" + n.noteId() + "\" date=" + n.date() + "]\n" + n.text() + "\n[/NOTE]")
        .collect(Collectors.joining("\n"));
  }
}
