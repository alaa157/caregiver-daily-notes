package com.caregiver.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Caps the note text rendered into a summary request.
 *
 * <p>The budget is an explicit character count, not a magic number, and covers
 * the whole rendered block including per-note delimiters. Selection is
 * deterministic and prefers the most recent notes (date, then id, descending).
 * Whole notes are kept where possible; only when the newest note alone cannot
 * fit is that note shortened. Everything omitted or shortened is counted so
 * the caller can tell the user.</p>
 */
public final class SummaryInputBudget {

  /** Maximum characters of assembled note text sent to the model. */
  public static final int MAX_NOTE_TEXT_CHARACTERS = 60_000;

  public record Result(List<SummaryNote> notes, String block, int truncatedNoteCount) {
  }

  private SummaryInputBudget() {
  }

  public static Result apply(List<SummaryNote> notes, LocalDate end, int days) {
    if (notes == null || notes.isEmpty() || end == null || days <= 0) {
      return new Result(List.of(), "", 0);
    }
    List<SummaryNote> newestFirst = notes.stream()
        .filter(note -> note != null && note.date() != null)
        .sorted(Comparator.comparing(SummaryNote::date).reversed()
            .thenComparing(Comparator.comparing(SummaryNote::noteId).reversed()))
        .toList();
    List<SummaryNote> kept = new ArrayList<>();
    int used = 0;
    int omitted = 0;
    boolean shortened = false;
    for (SummaryNote note : newestFirst) {
      String rendered = render(note);
      // SummaryDataBuilder joins notes with a newline; count that separator.
      if (used + rendered.length() + 1 <= MAX_NOTE_TEXT_CHARACTERS) {
        kept.add(note);
        used += rendered.length() + 1;
        continue;
      }
      if (kept.isEmpty()) {
        // Only the newest note is eligible to be shortened; it still wins over
        // dropping all evidence. The remainder cannot fit any further note.
        String wrapper = "[NOTE id=\"" + note.noteId() + "\" date=" + note.date() + "]\n";
        String closing = "\n[/NOTE]";
        int available = MAX_NOTE_TEXT_CHARACTERS - wrapper.length() - closing.length() - 1;
        if (available > 0) {
          String text = note.text() == null ? "" : note.text();
          kept.add(new SummaryNote(note.noteId(), note.date(), text.substring(0, Math.min(text.length(), available))));
          shortened = true;
        }
        break;
      }
      omitted++;
    }
    return new Result(List.copyOf(kept), SummaryDataBuilder.build(kept, end, days), omitted + (shortened ? 1 : 0));
  }

  private static String render(SummaryNote note) {
    return "[NOTE id=\"" + note.noteId() + "\" date=" + note.date() + "]\n"
        + (note.text() == null ? "" : note.text()) + "\n[/NOTE]";
  }
}
