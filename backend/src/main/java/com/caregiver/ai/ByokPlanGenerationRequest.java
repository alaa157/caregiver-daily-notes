package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** One plan-generation request paired with credentials used only for provider calls. */
public final class ByokPlanGenerationRequest {
  @NotNull @Valid
  private final SummaryRequest summary;
  @NotNull @Valid
  private final ProviderCredentials credentials;

  public ByokPlanGenerationRequest(SummaryRequest summary, ProviderCredentials credentials) {
    this.summary = summary;
    this.credentials = credentials;
  }

  @JsonCreator
  private static ByokPlanGenerationRequest fromJson(
      @JsonProperty("summary") SummaryRequest summary,
      @JsonProperty("credentials") JsonNode credentials) {
    return new ByokPlanGenerationRequest(summary, ByokSummaryRequest.parseCredentials(credentials));
  }

  public SummaryRequest summary() {
    return summary;
  }

  public ProviderCredentials credentials() {
    return credentials;
  }

}
