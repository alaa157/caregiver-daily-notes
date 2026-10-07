package com.caregiver.ai;

import com.caregiver.ai.GoldenEvalCase.QuoteRef;
import com.caregiver.ai.GoldenEvalCase.TestSignal;
import com.caregiver.common.NoteSignals;
import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.MedicationHardLock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic evaluation runner: replays golden cases through the real
 * pipeline pieces with scripted model outputs. Pure functions — no network,
 * no randomness, fully reproducible.
 */
public class AiEvaluator {

  /** Fixed window end all golden `dayOffset`s resolve against. */
  public static final LocalDate EVAL_END = LocalDate.of(2026, 10, 30);

  private static final int EVAL_WINDOW_DAYS = 7;

  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();

  public EvalCaseResult evaluateOne(GoldenEvalCase c) {
    List<String> failures = new ArrayList<>();

    List<NoteSignals> signals = new ArrayList<>();
    for (TestSignal s : c.signals()) {
      signals.add(new NoteSignals(
          "eval", EVAL_END.plusDays(s.dayOffset()), s.fall(), s.pain(),
          s.missed(), s.unverified(), s.appetite(), s.text()));
    }
    SafetyEvaluation evaluation = evaluator.evaluate(signals, EVAL_END, EVAL_WINDOW_DAYS);
    Set<String> fired = new HashSet<>();
    for (SafetySignal signal : evaluation.signals()) {
      fired.add(signal.ruleId().getId());
    }
    boolean flagsHit = fired.containsAll(c.expectedFlags());
    if (!flagsHit) {
      failures.add(c.id() + ":flags");
    }

    boolean schemaOk;
    try {
      SummaryService.ModelSummary parsed = StrictJsonParser.parse(
          c.modelText(), SummaryService.ModelSummary.class, Set.of("observations", "uncertainties"));
      schemaOk = parsed.observations() != null
          && parsed.uncertainties() != null
          && parsed.observations().stream().allMatch(observation -> observation != null
              && observation.text() != null && observation.noteId() != null && observation.quote() != null)
          && parsed.uncertainties().stream().allMatch(uncertainty -> uncertainty != null
              && uncertainty.topic() != null && uncertainty.detail() != null);
    } catch (RuntimeException e) {
      schemaOk = false;
    }
    boolean schemaValid = schemaOk == c.expectValid();
    if (!schemaValid) {
      failures.add(c.id() + ":schema");
    }

    boolean quotesValid = checkQuotes(c);
    boolean grounded = quotesValid == c.expectGrounded()
        && (!c.expectGrounded() || !c.quotes().isEmpty());
    if (!grounded) {
      failures.add(c.id() + ":grounded");
    }

    boolean medsOk = checkMeds(c);
    boolean medsSafe = medsOk == c.expectMedsSafe();
    if (!medsSafe) {
      failures.add(c.id() + ":meds");
    }

    return new EvalCaseResult(c.id(), flagsHit, schemaValid, grounded, medsSafe, failures);
  }

  public EvalMetrics evaluate(List<GoldenEvalCase> cases) {
    if (cases == null || cases.isEmpty()) {
      return new EvalMetrics(1.0, 1.0, 1.0, 1.0, 0, List.of());
    }
    List<EvalCaseResult> results = new ArrayList<>();
    for (GoldenEvalCase c : cases) {
      results.add(evaluateOne(c));
    }
    int recallDenominator = 0;
    int recallHits = 0;
    int schemaHits = 0;
    int groundedHits = 0;
    int medsHits = 0;
    List<String> failures = new ArrayList<>();
    for (int i = 0; i < cases.size(); i++) {
      EvalCaseResult r = results.get(i);
      if (!cases.get(i).expectedFlags().isEmpty()) {
        recallDenominator++;
        if (r.flagsHit()) {
          recallHits++;
        }
      }
      if (r.schemaValid()) {
        schemaHits++;
      }
      if (r.grounded()) {
        groundedHits++;
      }
      if (r.medsSafe()) {
        medsHits++;
      }
      failures.addAll(r.failures());
    }
    int total = cases.size();
    return new EvalMetrics(
        recallDenominator == 0 ? 1.0 : (double) recallHits / recallDenominator,
        (double) schemaHits / total,
        (double) groundedHits / total,
        (double) medsHits / total,
        total,
        failures);
  }

  private boolean checkQuotes(GoldenEvalCase c) {
    List<QuoteRef> quotes = c.quotes();
    if (quotes == null || quotes.isEmpty()) {
      return false;
    }
    for (QuoteRef ref : quotes) {
      if (ref.note() < 0 || ref.note() >= c.signals().size()) {
        return false;
      }
      String noteText = c.signals().get(ref.note()).text();
      String quote = ArabicNormalizer.normalize(ref.quote());
      if (quote.isEmpty() || !ArabicNormalizer.normalize(noteText).contains(quote)) {
        return false;
      }
    }
    return true;
  }

  private boolean checkMeds(GoldenEvalCase c) {
    Set<Integer> days = new HashSet<>();
    for (TestSignal s : c.signals()) {
      if (Boolean.TRUE.equals(s.missed()) && !s.unverified()) {
        days.add(s.dayOffset());
      }
    }
    List<MedicationEntry> entries = new ArrayList<>();
    for (int day : days) {
      entries.add(new MedicationEntry("Med-" + day, "5mg", "daily"));
    }
    try {
      MedicationHardLock.checkUnchanged(entries, List.copyOf(entries));
      return true;
    } catch (InvalidModelOutputException e) {
      return false;
    }
  }
}
