package com.caregiver.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryRequestTest {

  private static final LocalDate END = LocalDate.of(2026, 10, 30);

  private static SummaryNote note(String id, LocalDate date, String text) {
    return new SummaryNote(id, date, text);
  }

  @Test
  @DisplayName("accepts only 7 14 and 30 day windows")
  void acceptsOnly7_14And30DayWindows() {
    assertThat(new SummaryRequest("r1", END, 7).periodDays()).isEqualTo(7);
    assertThat(new SummaryRequest("r1", END, 14).periodDays()).isEqualTo(14);
    assertThat(new SummaryRequest("r1", END, 30).periodDays()).isEqualTo(30);
    assertThatThrownBy(() -> new SummaryRequest("r1", END, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SummaryRequest("r1", END, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SummaryRequest("r1", END, 31)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SummaryRequest("r1", END, -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SummaryRequest("  ", END, 7)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SummaryRequest("r1", null, 7)).isInstanceOf(NullPointerException.class);

    assertWindow(7, LocalDate.of(2026, 10, 24));
    assertWindow(14, LocalDate.of(2026, 10, 17));
    assertWindow(30, LocalDate.of(2026, 10, 1));
  }

  private static void assertWindow(int periodDays, LocalDate expectedStart) {
    LocalDate startInclusive = END.minusDays(periodDays - 1L);
    assertThat(startInclusive).isEqualTo(expectedStart);
    assertThat(ChronoUnit.DAYS.between(startInclusive, END) + 1).isEqualTo(periodDays);
  }

  @Test
  @DisplayName("sorts same date notes by note id")
  void sortsSameDateNotesByNoteId() {
    var block = SummaryDataBuilder.build(List.of(
        note("n2", END, "second"),
        note("n1", END, "first")), END, 7);

    assertThat(block).containsSubsequence("id=\"n1\"", "id=\"n2\"");
  }

  @Test
  @DisplayName("includes the first and last dates of the requested window")
  void includesTheFirstAndLastDatesOfTheRequestedWindow() {
    LocalDate first = END.minusDays(6);
    var block = SummaryDataBuilder.build(List.of(
        note("first", first, "first included date"),
        note("last", END, "last included date")), END, 7);

    assertThat(block).contains("first included date", "last included date");
  }

  @Test
  @DisplayName("excludes dates immediately outside the requested window")
  void excludesDatesImmediatelyOutsideTheRequestedWindow() {
    var block = SummaryDataBuilder.build(List.of(
        note("before", END.minusDays(7), "before the window"),
        note("after", END.plusDays(1), "after the window")), END, 7);

    assertThat(block).doesNotContain("before the window", "after the window");
  }

  @Test
  void builder_nullListYieldsEmpty() {
    assertThat(SummaryDataBuilder.build(null, END, 7)).isEmpty();
    assertThat(SummaryDataBuilder.build(List.of(), END, 7)).isEmpty();
  }

  @Test
  void builder_rendersDelimitedNotesInDateOrder() {
    var block = SummaryDataBuilder.build(List.of(
        note("n2", END, "second"),
        note("n1", END.minusDays(1), "first")), END, 7);

    assertThat(block).isEqualTo(
        "[NOTE id=\"n1\" date=2026-10-29]\nfirst\n[/NOTE]\n[NOTE id=\"n2\" date=2026-10-30]\nsecond\n[/NOTE]");
  }

  @Test
  void builder_excludesOutOfWindowNotes() {
    var block = SummaryDataBuilder.build(List.of(
        note("old", LocalDate.of(2026, 9, 30), "too old"),
        note("edge", LocalDate.of(2026, 10, 1), "day one")), END, 30);

    assertThat(block).doesNotContain("too old");
    assertThat(block).contains("day one");
  }

}
