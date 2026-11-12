package com.caregiver.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caregiver.common.NoteSignals;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class SummaryApplicationServiceTest {
  private static final LocalDate END = LocalDate.of(2026, 11, 12);
  private static final List<NoteSignals> FALLS = List.of(
      signal(LocalDate.of(2026, 11, 2)), signal(LocalDate.of(2026, 11, 4)));

  @ParameterizedTest
  @ValueSource(ints = {7, 14, 30})
  void successProviderFailureAndNoNotesUseIdenticalRequestedPeriodTrends(int days) {
    SummaryRequest request = request(days);
    SummaryResult success = run(request, List.of(new SummaryNote("note", END, "routine")), FALLS, "success");
    SummaryResult failure = run(request, List.of(new SummaryNote("note", END, "routine")), FALLS, "failure");
    SummaryResult noNotes = run(request, List.of(), FALLS, "no-notes");

    assertThat(failure.trends()).isEqualTo(success.trends());
    assertThat(noNotes.trends()).isEqualTo(success.trends());
    for (SummaryResult result : List.of(success, failure, noNotes)) {
      TrendResult falls = trend(result, "FALL_COUNT");
      assertThat(falls.currentCount()).isEqualTo(days == 7 ? 0 : 2);
      assertThat(falls.previousCount()).isEqualTo(days == 7 ? 2 : 0);
    }
  }

  @Test
  void noNotesIsSuccessfulEmptySummaryAndStillReturnsSafetyAndTrends() {
    SummaryResult result = run(request(14), List.of(), FALLS, "no-notes");

    assertThat(result.summary().aiUnavailable()).isFalse();
    assertThat(result.summary().observations()).isEmpty();
    assertThat(result.summary().uncertainties()).isEmpty();
    assertThat(result.safety().signals()).extracting(SafetySignal::ruleId)
        .containsExactly(SafetyRuleId.FALL_DETECTED);
    assertThat(trend(result, "FALL_COUNT").currentCount()).isEqualTo(2);
  }

  @Test
  void providerFailureRemainsUnavailableWithSafetyAndRequestedPeriodTrends() {
    SummaryResult result = run(request(14), List.of(new SummaryNote("note", END, "routine")), FALLS, "failure");

    assertThat(result.summary().aiUnavailable()).isTrue();
    assertThat(result.summary().observations()).isEmpty();
    assertThat(result.summary().uncertainties()).extracting(SummaryUncertainty::detail)
        .containsExactly("AI unavailable");
    assertThat(result.safety().signals()).extracting(SafetySignal::ruleId)
        .containsExactly(SafetyRuleId.FALL_DETECTED);
    assertThat(trend(result, "FALL_COUNT").currentCount()).isEqualTo(2);
  }

  @Test
  void previousEndAndCurrentStartAreInclusiveFor14And30DayWindows() {
    for (int days : List.of(14, 30)) {
      LocalDate currentStart = END.minusDays(days - 1L);
      List<NoteSignals> boundarySignals = List.of(
          signal(currentStart.minusDays(1)), signal(currentStart));
      List<SummaryNote> notes = List.of(new SummaryNote("note", END, "routine"));
      SummaryResult success = run(request(days), notes, boundarySignals, "success");
      SummaryResult failure = run(request(days), notes, boundarySignals, "failure");
      SummaryResult noNotes = run(request(days), List.of(), boundarySignals, "no-notes");

      for (SummaryResult result : List.of(success, failure, noNotes)) {
        TrendResult falls = trend(result, "FALL_COUNT");
        assertThat(falls.previousCount()).isEqualTo(1);
        assertThat(falls.currentCount()).isEqualTo(1);
        assertThat(falls.previousSampleSize()).isEqualTo(1);
        assertThat(falls.currentSampleSize()).isEqualTo(1);
      }
    }
  }

  @Test
  void cancellationStillPropagates() {
    SummarySourceLoader loader = mock(SummarySourceLoader.class);
    ProviderDeadline provider = mock(ProviderDeadline.class);
    SummaryRequest request = request(14);
    when(loader.load(request)).thenReturn(new SummarySourceLoader.Source(
        List.of(new SummaryNote("note", END, "routine")), FALLS));
    doThrow(new CancellationException("cancelled")).when(provider).complete(any());

    assertThatThrownBy(() -> new SummaryApplicationService(loader, provider).generate(request))
        .isInstanceOf(CancellationException.class);
  }

  private SummaryResult run(SummaryRequest request, List<SummaryNote> notes, List<NoteSignals> signals, String mode) {
    SummarySourceLoader loader = mock(SummarySourceLoader.class);
    ProviderDeadline provider = mock(ProviderDeadline.class);
    when(loader.load(request)).thenReturn(new SummarySourceLoader.Source(notes, signals));
    if (mode.equals("failure")) {
      doThrow(new IllegalStateException("synthetic provider failure")).when(provider).complete(any());
    } else if (mode.equals("success")) {
      when(provider.complete(any())).thenReturn(new LlmResult(LlmStatus.OK,
          "{\"observations\":[{\"text\":\"routine\",\"noteId\":\"note\",\"quote\":\"routine\"}],\"uncertainties\":[]}", null));
    }
    SummaryResult result = new SummaryApplicationService(loader, provider).generate(request).result();
    if (mode.equals("no-notes")) verifyNoInteractions(provider);
    return result;
  }

  private static SummaryRequest request(int days) {
    return new SummaryRequest("recipient", END, days);
  }

  private static NoteSignals signal(LocalDate date) {
    return new NoteSignals("recipient", date, true, null, null, false, null, "");
  }

  private static TrendResult trend(SummaryResult result, String metric) {
    return result.trends().stream().filter(item -> item.metric().equals(metric)).findFirst().orElseThrow();
  }
}
