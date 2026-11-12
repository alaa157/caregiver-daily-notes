package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.springframework.stereotype.Service;

/** Caller data cannot replace owned notes or authoritative deterministic safety. */
@Service
public class SummaryApplicationService {
  private final SummarySourceLoader loader;
  private final SummaryService summaries;
  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();
  private final TrendCalculator trends = new TrendCalculator();
  public SummaryApplicationService(SummarySourceLoader loader, ProviderDeadline provider) {
    this.loader = loader; this.summaries = new SummaryService(provider::complete, evaluator, trends);
  }
  public record Generated(SummaryResult result, List<SummaryNote> notes) {}
  public SummaryResult summarize(SummaryRequest request) { return generate(request).result(); }
  public Generated generate(SummaryRequest request) {
    SummarySourceLoader.Source source = loader.load(request);
    return summarizeLoaded(request, source, null);
  }
  public Generated generate(SummaryRequest request, ProviderCredentials credentials) {
    SummarySourceLoader.Source source = loader.load(request);
    return summarizeLoaded(request, source, credentials);
  }
  public SummaryResult generate(ByokSummaryRequest request) {
    return generate(request.summary(), request.credentials()).result();
  }
  private Generated summarizeLoaded(SummaryRequest request, SummarySourceLoader.Source source,
      ProviderCredentials credentials) {
    SummaryResult result;
    try {
      result = source.notes().isEmpty() ? noNotes(request, source.signals())
          : summaries.summarize(request, source.notes(), source.signals(), credentials);
    } catch (CancellationException cancelled) {
      throw cancelled;
    } catch (RuntimeException providerOrValidationFailure) {
      // Preserve request cancellation instead of reporting it as AI unavailability.
      if (Thread.currentThread().isInterrupted()) {
        throw new CancellationException("Summary generation interrupted");
      }
      // Ownership/data reads happen outside this catch. Invalid model output
      // and provider failures erase model content, never authoritative safety.
      result = unavailable(request, source.signals());
    }
    return new Generated(result, source.notes());
  }
  private SummaryResult noNotes(SummaryRequest request, List<NoteSignals> signals) {
    return result(request, signals, new GroundedSummary(List.of(), List.of(), false));
  }
  private SummaryResult unavailable(SummaryRequest request, List<NoteSignals> signals) {
    return result(request, signals,
        new GroundedSummary(List.of(), List.of(new SummaryUncertainty("availability", "AI unavailable")), true));
  }
  private SummaryResult result(SummaryRequest request, List<NoteSignals> signals, GroundedSummary summary) {
    return new SummaryResult(summary, evaluator.evaluate(signals, request.periodEndInclusive(), request.periodDays()),
        TrendWindows.compute(trends, signals, request.periodEndInclusive(), request.periodDays()));
  }
}
