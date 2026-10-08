package com.caregiver.ai;

import com.caregiver.common.NoteSignals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates grounded summarization: builds the delimited data block,
 * calls the provider, parses strictly, and validates evidence fail-closed.
 * Safety and trends always come from deterministic backend code. A provider
 * fallback yields an explicit unavailable summary with safety intact —
 * never an invented one.
 */
public class SummaryService {

  private final LlmClient llm;
  private final SafetySignalEvaluator evaluator;
  private final TrendCalculator trends;

  public SummaryService(LlmClient llm, SafetySignalEvaluator evaluator, TrendCalculator trends) {
    this.llm = Objects.requireNonNull(llm, "llm");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    this.trends = Objects.requireNonNull(trends, "trends");
  }

  public SummaryResult summarize(AiSummaryRequest request, List<SummaryNote> notes, List<NoteSignals> signals) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    LocalDate end = request.periodEndInclusive();
    int days = request.periodDays();
    List<SummaryNote> windowed = windowed(notes, end, days);

    Map<String, String> noteTextsById = new LinkedHashMap<>();
    for (SummaryNote note : windowed) {
      noteTextsById.putIfAbsent(note.noteId(), note.text());
    }
    String block = SummaryDataBuilder.build(windowed, end, days);

    List<NoteSignals> safeSignals = signals == null ? List.of() : signals;
    SafetyEvaluation safety = evaluator.evaluate(safeSignals, end, days);
    List<TrendResult> trendResults = trends.computeTrends(
        inRange(safeSignals, end.minusDays(6), end),
        inRange(safeSignals, end.minusDays(13), end.minusDays(7)));

    LlmResult result = llm.complete(new LlmRequest(SummaryPrompts.SYSTEM_PROMPT, block));
    if (result.status() == LlmStatus.FALLBACK) {
      return new SummaryResult(
          new GroundedSummary(
              List.of(),
              List.of(new SummaryUncertainty("availability", "AI unavailable")),
              true),
          safety,
          trendResults);
    }
    ModelSummary parsed =
        StrictJsonParser.parse(result.text(), ModelSummary.class, Set.of("observations", "uncertainties"));
    GroundedSummary summary = new GroundedSummary(parsed.observations(), parsed.uncertainties(), false);
    return new SummaryResult(EvidenceValidator.validate(summary, noteTextsById), safety, trendResults);
  }

  /** The model's permitted JSON shape; provider status is owned by this service. */
  public record ModelSummary(List<SummaryObservation> observations, List<SummaryUncertainty> uncertainties) {
  }

  private static List<SummaryNote> windowed(List<SummaryNote> notes, LocalDate end, int days) {
    if (notes == null || notes.isEmpty()) {
      return List.of();
    }
    LocalDate start = end.minusDays((long) days - 1);
    List<SummaryNote> windowed = new ArrayList<>();
    for (SummaryNote note : notes) {
      if (note != null && note.date() != null
          && !note.date().isBefore(start)
          && !note.date().isAfter(end)) {
        windowed.add(note);
      }
    }
    return windowed;
  }

  private static List<NoteSignals> inRange(List<NoteSignals> signals, LocalDate start, LocalDate end) {
    List<NoteSignals> ranged = new ArrayList<>();
    for (NoteSignals signal : signals) {
      if (signal != null && signal.date() != null
          && !signal.date().isBefore(start)
          && !signal.date().isAfter(end)) {
        ranged.add(signal);
      }
    }
    return ranged;
  }
}
