package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryInputBudgetTest {

  private static final LocalDate END = LocalDate.of(2026, 10, 30);

  private static SummaryNote note(int index, int chars) {
    return new SummaryNote("n" + index, END.minusDays(index), "x".repeat(chars));
  }

  @Test
  void withinBudgetIsSentUnmodified() {
    List<SummaryNote> notes = List.of(note(0, 100), note(1, 120));

    var result = SummaryInputBudget.apply(notes, END, 7);

    assertThat(result.block()).isEqualTo(SummaryDataBuilder.build(notes, END, 7));
    assertThat(result.truncatedNoteCount()).isZero();
    assertThat(result.notes()).containsExactlyElementsOf(notes);
  }

  @Test
  void overBudgetKeepsNewestWholeNotesAndCountsOmittedOnes() {
    List<SummaryNote> notes = List.of(note(0, 25_000), note(1, 25_000), note(2, 25_000), note(3, 25_000));

    var result = SummaryInputBudget.apply(notes, END, 30);

    assertThat(result.block().length()).isLessThanOrEqualTo(SummaryInputBudget.MAX_NOTE_TEXT_CHARACTERS);
    assertThat(result.notes()).extracting(SummaryNote::noteId).containsExactly("n0", "n1");
    assertThat(result.truncatedNoteCount()).isEqualTo(2);
    assertThat(result.block()).contains("id=\"n0\"", "id=\"n1\"").doesNotContain("id=\"n2\"", "id=\"n3\"");
  }

  @Test
  void singleOversizedNoteIsShortenedAndCounted() {
    var result = SummaryInputBudget.apply(List.of(note(0, 120_000)), END, 7);

    assertThat(result.block().length()).isLessThanOrEqualTo(SummaryInputBudget.MAX_NOTE_TEXT_CHARACTERS);
    assertThat(result.notes()).hasSize(1);
    assertThat(result.notes().getFirst().text().length()).isLessThan(120_000);
    assertThat(result.truncatedNoteCount()).isEqualTo(1);
  }

  @Test
  void truncationIsDeterministic() {
    List<SummaryNote> notes = List.of(note(0, 25_000), note(1, 25_000), note(2, 25_000));

    assertThat(SummaryInputBudget.apply(notes, END, 30))
        .isEqualTo(SummaryInputBudget.apply(notes, END, 30));
  }
}
