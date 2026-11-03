package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
abstract class ObservationColumns {
  @Column(name = "mood", length = 40, updatable = false) private String legacyMood;
  @Enumerated(EnumType.STRING) @Column(name = "mood_state", nullable = false, length = 32, updatable = false) private ObservationState moodState;
  @Column(name = "mood_value", length = 40, updatable = false) private String moodValue;
  @Column(name = "appetite", length = 40, updatable = false) private String legacyAppetite;
  @Enumerated(EnumType.STRING) @Column(name = "appetite_state", nullable = false, length = 32, updatable = false) private ObservationState appetiteState;
  @Column(name = "appetite_value", length = 40, updatable = false) private String appetiteValue;
  @Column(name = "sleep", length = 40, updatable = false) private String legacySleep;
  @Enumerated(EnumType.STRING) @Column(name = "sleep_state", nullable = false, length = 32, updatable = false) private ObservationState sleepState;
  @Column(name = "sleep_value", length = 40, updatable = false) private String sleepValue;
  @Column(name = "mobility", length = 40, updatable = false) private String legacyMobility;
  @Enumerated(EnumType.STRING) @Column(name = "mobility_state", nullable = false, length = 32, updatable = false) private ObservationState mobilityState;
  @Column(name = "mobility_value", length = 40, updatable = false) private String mobilityValue;
  @Column(name = "medication_status", length = 20, updatable = false) private String legacyMedicationStatus;
  @Enumerated(EnumType.STRING) @Column(name = "medication_status_state", nullable = false, length = 32, updatable = false) private ObservationState medicationStatusState;
  @Column(name = "medication_status_value", length = 20, updatable = false) private String medicationStatusValue;
  @Column(name = "medication_verified", updatable = false) private Boolean legacyMedicationVerified;
  @Enumerated(EnumType.STRING) @Column(name = "medication_verification_state", nullable = false, length = 20, updatable = false) private VerificationState medicationVerification;
  @Column(name = "medication_verification_provided", nullable = false, updatable = false) private boolean medicationVerificationProvided;
  @Column(name = "pain_score", updatable = false) private Integer legacyPainScore;
  @Enumerated(EnumType.STRING) @Column(name = "pain_score_state", nullable = false, length = 32, updatable = false) private ObservationState painScoreState;
  @Column(name = "pain_score_value", updatable = false) private Integer painScoreValue;
  @Column(name = "fall_reported", updatable = false) private Boolean legacyFallReported;
  @Enumerated(EnumType.STRING) @Column(name = "fall_reported_state", nullable = false, length = 32, updatable = false) private ObservationState fallReportedState;
  @Column(name = "fall_reported_value", updatable = false) private Boolean fallReportedValue;
  @Column(name = "free_text", columnDefinition = "TEXT", updatable = false) private String legacyFreeText;
  @Enumerated(EnumType.STRING) @Column(name = "free_text_state", nullable = false, length = 32, updatable = false) private ObservationState freeTextState;
  @Column(name = "free_text_value", columnDefinition = "TEXT", updatable = false) private String freeTextValue;

  protected ObservationColumns() {}

  protected ObservationColumns(NoteObservations observations, boolean verificationProvided) {
    setMood(observations.mood());
    setAppetite(observations.appetite());
    setSleep(observations.sleep());
    setMobility(observations.mobility());
    setMedicationStatus(observations.medicationStatus());
    medicationVerification = observations.medicationVerification();
    medicationVerificationProvided = verificationProvided;
    legacyMedicationVerified = switch (medicationVerification) {
      case VERIFIED -> true;
      case UNVERIFIED -> false;
      case NOT_ASSERTED -> null;
    };
    setPainScore(observations.painScore());
    setFallReported(observations.fallReported());
    setFreeText(observations.freeText());
  }

  private void setMood(Observation<String> observation) {
    moodState = observation.state();
    moodValue = observation.value();
    legacyMood = compatibilityValue(observation);
  }

  private void setAppetite(Observation<String> observation) {
    appetiteState = observation.state();
    appetiteValue = observation.value();
    legacyAppetite = compatibilityValue(observation);
  }

  private void setSleep(Observation<String> observation) {
    sleepState = observation.state();
    sleepValue = observation.value();
    legacySleep = compatibilityValue(observation);
  }

  private void setMobility(Observation<String> observation) {
    mobilityState = observation.state();
    mobilityValue = observation.value();
    legacyMobility = compatibilityValue(observation);
  }

  private void setMedicationStatus(Observation<String> observation) {
    medicationStatusState = observation.state();
    medicationStatusValue = observation.value();
    legacyMedicationStatus = observation.state() == ObservationState.UNKNOWN
        ? "UNKNOWN" : compatibilityValue(observation);
  }

  private void setPainScore(Observation<Integer> observation) {
    painScoreState = observation.state();
    painScoreValue = observation.value();
    legacyPainScore = compatibilityValue(observation);
  }

  private void setFallReported(Observation<Boolean> observation) {
    fallReportedState = observation.state();
    fallReportedValue = observation.value();
    legacyFallReported = compatibilityValue(observation);
  }

  private void setFreeText(Observation<String> observation) {
    freeTextState = observation.state();
    freeTextValue = observation.value();
    legacyFreeText = compatibilityValue(observation);
  }

  private static <T> T compatibilityValue(Observation<T> observation) {
    return observation.state() == ObservationState.RECORDED
        ? observation.value()
        : observation.state() == ObservationState.LEGACY_AMBIGUOUS ? observation.rawValue() : null;
  }

  public final NoteObservations observations() {
    return new NoteObservations(
        observation(moodState, moodValue, legacyMood),
        observation(appetiteState, appetiteValue, legacyAppetite),
        observation(sleepState, sleepValue, legacySleep),
        observation(mobilityState, mobilityValue, legacyMobility),
        observation(medicationStatusState, medicationStatusValue, legacyMedicationStatus),
        observation(painScoreState, painScoreValue, legacyPainScore),
        observation(fallReportedState, fallReportedValue, legacyFallReported),
        observation(freeTextState, freeTextValue, legacyFreeText),
        medicationVerification);
  }

  private static <T> Observation<T> observation(
      ObservationState state, T value, T legacyValue) {
    return state == ObservationState.LEGACY_AMBIGUOUS
        ? Observation.legacyAmbiguous(legacyValue)
        : new Observation<>(state, value, null);
  }

  final String getMood() { return display(moodState, moodValue, legacyMood); }
  final String getAppetite() { return display(appetiteState, appetiteValue, legacyAppetite); }
  final String getSleep() { return display(sleepState, sleepValue, legacySleep); }
  final String getMobility() { return display(mobilityState, mobilityValue, legacyMobility); }
  final String getMedicationStatus() {
    if (medicationStatusState == ObservationState.UNKNOWN) return "UNKNOWN";
    return display(medicationStatusState, medicationStatusValue, legacyMedicationStatus);
  }
  final Integer getPainScore() { return display(painScoreState, painScoreValue, legacyPainScore); }
  final Boolean getFallReported() { return display(fallReportedState, fallReportedValue, legacyFallReported); }
  final String getFreeText() { return display(freeTextState, freeTextValue, legacyFreeText); }
  final Boolean getMedicationVerified() { return legacyMedicationVerified; }
  final VerificationState getMedicationVerification() { return medicationVerification; }
  final boolean isMedicationVerificationProvided() { return medicationVerificationProvided; }

  private static <T> T display(ObservationState state, T value, T legacyValue) {
    return state == ObservationState.RECORDED
        ? value : state == ObservationState.LEGACY_AMBIGUOUS ? legacyValue : null;
  }
}
