package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSummaryRequestTest {

  private static final LocalDate END = LocalDate.of(2026, 10, 30);

  private static SummaryNote note(String id, LocalDate date, String text) {
    return new SummaryNote(id, date, text);
  }

  @Test
  void periods7_14_30_accepted_othersRejected() {
    assertThat(new AiSummaryRequest("r1", END, 7).periodDays()).isEqualTo(7);
    assertThat(new AiSummaryRequest("r1", END, 14).periodDays()).isEqualTo(14);
    assertThat(new AiSummaryRequest("r1", END, 30).periodDays()).isEqualTo(30);
    assertThatThrownBy(() -> new AiSummaryRequest("r1", END, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AiSummaryRequest("r1", END, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AiSummaryRequest("  ", END, 7)).isInstanceOf(IllegalArgumentException.class);
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

  @Test
  void builder_nullListYieldsEmpty() {
    assertThat(SummaryDataBuilder.build(null, END, 7)).isEmpty();
    assertThat(SummaryDataBuilder.build(List.of(), END, 7)).isEmpty();
  }
}
