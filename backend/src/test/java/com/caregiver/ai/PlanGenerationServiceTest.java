package com.caregiver.ai;

import com.caregiver.plans.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies plan-generation failures are logged by a stable, content-free reason code. */
class PlanGenerationServiceTest {
  private static final String RECIPIENT_ID = "11999999-9999-4999-8999-999999999999";
  private static final String NOTE_ID = "22888888-8888-4888-8888-888888888888";
  private static final String HEALTH_DATA = "Syntheticpainkiller-SECRET";

  private final SummaryApplicationService summaries = mock(SummaryApplicationService.class);
  private final PlanService plans = mock(PlanService.class);
  private ListAppender<ILoggingEvent> appender;
  private Logger serviceLogger;

  @BeforeEach
  void attachAppender() {
    serviceLogger = (Logger) LoggerFactory.getLogger(PlanGenerationService.class);
    appender = new ListAppender<>();
    appender.start();
    serviceLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    serviceLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void invalidModelOutputIsLoggedWithReasonCodeAndNoHealthData() {
    PlanGenerationService service = serviceReturning(new LlmResult(LlmStatus.OK, "not-json", ""));
    when(summaries.generate(any(SummaryRequest.class), any(ProviderCredentials.class)))
        .thenAnswer(invocation -> generated());

    var result = service.generate(request());

    assertThat(result.aiUnavailable()).isTrue();
    assertThat(formattedMessages()).anySatisfy(message -> {
      assertThat(message).contains("plan_generation_failed")
          .contains(PlanFailureCodes.INVALID_MODEL_OUTPUT)
          .contains(RECIPIENT_ID);
      assertThat(message).doesNotContain(HEALTH_DATA);
    });
  }

  @Test
  void medicationLockFailureIsLoggedWithItsOwnReasonCode() {
    // The provider proposes a changed medication, so the hard lock rejects it.
    String planJson = "{\"changes\":[{\"actionId\":\"SCHEDULE_CHECK_IN\",\"params\":{\"withinDays\":\"7\"},"
        + "\"reason\":\"Review logged observations.\",\"evidenceNoteIds\":[\"" + NOTE_ID + "\"]}],"
        + "\"proposedMedications\":[{\"name\":\"" + HEALTH_DATA + "\",\"dose\":\"10 mg\",\"schedule\":\"daily\"}]}";
    PlanGenerationService service = serviceReturning(new LlmResult(LlmStatus.OK, planJson, ""));
    when(summaries.generate(any(SummaryRequest.class), any(ProviderCredentials.class)))
        .thenAnswer(invocation -> generated());

    var result = service.generate(request());

    assertThat(result.aiUnavailable()).isTrue();
    assertThat(formattedMessages()).anySatisfy(message -> {
      assertThat(message).contains("plan_generation_failed")
          .contains(PlanFailureCodes.MEDICATION_LOCK);
      assertThat(message).doesNotContain(HEALTH_DATA);
    });
  }

  @Test
  void textSafetyFailureIsLoggedWithItsOwnReasonCode() {
    String planJson = "{\"changes\":[{\"actionId\":\"SCHEDULE_CHECK_IN\",\"params\":{\"withinDays\":\"7\"},"
        + "\"reason\":\"Prescribe a strong antibiotic.\",\"evidenceNoteIds\":[\"" + NOTE_ID + "\"]}],"
        + "\"proposedMedications\":[]}";
    PlanGenerationService service = serviceReturning(new LlmResult(LlmStatus.OK, planJson, ""));
    when(summaries.generate(any(SummaryRequest.class), any(ProviderCredentials.class)))
        .thenAnswer(invocation -> generated());

    var result = service.generate(request());

    assertThat(result.aiUnavailable()).isTrue();
    assertThat(formattedMessages()).anySatisfy(message -> {
      assertThat(message).contains("plan_generation_failed")
          .contains(PlanFailureCodes.TEXT_SAFETY);
      assertThat(message).doesNotContain("Prescribe a strong antibiotic.");
    });
  }

  private List<String> formattedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private PlanGenerationService serviceReturning(LlmResult providerResult) {
    LlmClient llm = request -> providerResult;
    ProviderDeadline provider = new ProviderDeadline(llm, 1000);
    return new PlanGenerationService(summaries, plans, provider, new ObjectMapper());
  }

  private SummaryApplicationService.Generated generated() {
    var summary = new GroundedSummary(
        List.of(new SummaryObservation("Observation text", NOTE_ID, "verbatim quote")), List.of(), false);
    var result = new SummaryResult(summary, new SafetyEvaluation(List.of(), false), List.of(), 1, 7);
    return new SummaryApplicationService.Generated(result,
        List.of(new SummaryNote(NOTE_ID, LocalDate.of(2026, 10, 26), "note text")));
  }

  private ByokPlanGenerationRequest request() {
    return new ByokPlanGenerationRequest(
        new SummaryRequest(RECIPIENT_ID, LocalDate.of(2026, 10, 26), 7),
        new ProviderCredentials("GEMINI", "https://generativelanguage.googleapis.com",
            "synthetic-model", "synthetic-key"));
  }
}
