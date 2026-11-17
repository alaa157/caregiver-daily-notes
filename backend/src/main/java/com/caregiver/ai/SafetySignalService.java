package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.notes.NoteSignalService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes deterministic red flags over an owned recipient's note period.
 *
 * <p>This is the provider-free surface for the same rules the summary path
 * uses: the projection comes from {@link NoteSignalService} and the decision
 * table from {@link SafetySignalEvaluator}. No LLM client or provider
 * credential is involved.</p>
 */
@Service
public class SafetySignalService {
  public static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 14, 30);

  private final NoteSignalService signals;
  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();

  public SafetySignalService(NoteSignalService signals) {
    this.signals = signals;
  }

  @Transactional(readOnly = true)
  public SafetyEvaluation evaluate(UUID recipientId, LocalDate periodEndInclusive, int periodDays) {
    if (recipientId == null || periodEndInclusive == null || !ALLOWED_PERIODS.contains(periodDays)) {
      throw new IllegalArgumentException("recipientId, periodEndInclusive and a supported periodDays are required.");
    }
    LocalDate from = periodEndInclusive.minusDays(periodDays - 1L);
    List<NoteSignals> notes = signals.forRange(recipientId, from, periodEndInclusive);
    return evaluator.evaluate(notes, periodEndInclusive, periodDays);
  }
}
