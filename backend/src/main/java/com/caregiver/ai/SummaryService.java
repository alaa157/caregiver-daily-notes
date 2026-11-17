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
 * calls the provider, parses strictly, and checks evidence fail-closed per
 * observation: unverifiable quotes are dropped individually and counted, while
 * structural failures still fail the whole request. Safety and trends always
 * come from deterministic backend code. A provider fallback or a response with
 * no surviving observations yields an explicit unavailable summary with safety
 * intact — never an invented one.
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

  public SummaryResult summarize(SummaryRequest request, List<SummaryNote> notes, List<NoteSignals> signals) {
    return summarize(request, notes, signals, null);
  }
  public SummaryResult summarize(SummaryRequest request, List<SummaryNote> notes, List<NoteSignals> signals,
      ProviderCredentials credentials) {
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
    int daysWithNotes = (int) windowed.stream().map(SummaryNote::date).distinct().count();
    String block = SummaryDataBuilder.build(windowed, end, days);

    List<NoteSignals> safeSignals = signals == null ? List.of() : signals;
    SafetyEvaluation safety = evaluator.evaluate(safeSignals, end, days);
    List<TrendResult> trendResults = TrendWindows.compute(trends, safeSignals, end, days);

    LlmResult result = llm.complete(new LlmRequest(SummaryPrompts.SYSTEM_PROMPT, block, credentials));
    if (result.status() == LlmStatus.FALLBACK) {
      String unavailableReason = result.failureReason() == null
          ? FailureReason.PROVIDER_UNAVAILABLE.name()
          : result.failureReason().name();
      return new SummaryResult(
          new GroundedSummary(
              List.of(),
              List.of(new SummaryUncertainty("availability", "AI unavailable")),
              true,
              unavailableReason),
          safety,
          trendResults,
          daysWithNotes,
          days);
    }
    ModelSummary parsed =
        StrictJsonParser.parse(result.text(), ModelSummary.class, Set.of("observations", "uncertainties"));
    EvidenceValidation validation = EvidenceValidator.validatePartial(
        new GroundedSummary(parsed.observations(), parsed.uncertainties(), false), noteTextsById);
    int dropped = validation.rejected().size();
    if (validation.accepted().isEmpty() && !parsed.observations().isEmpty()) {
      // No observation survived verification: fail cleanly with an explicit
      // state rather than presenting an empty summary as a success.
      return new SummaryResult(new GroundedSummary(List.of(), parsed.uncertainties(), true,
          FailureReason.INVALID_OUTPUT.name(), dropped, validation.reason()), safety, trendResults,
          daysWithNotes, days);
    }
    GroundedSummary grounded = new GroundedSummary(validation.accepted(), parsed.uncertainties(), false,
        null, dropped, dropped == 0 ? null : validation.reason());
    return new SummaryResult(grounded, safety, trendResults, daysWithNotes, days);
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

}
