package com.caregiver.ai;

import org.springframework.stereotype.Service;

@Service
public class ByokConnectionTestService {
  private static final String SYSTEM_PROMPT = "Return a brief JSON connection confirmation.";
  private static final String PROBE_DATA = "connection test ping";

  private final ProviderDeadline providerDeadline;

  public ByokConnectionTestService(ProviderDeadline providerDeadline) {
    this.providerDeadline = providerDeadline;
  }

  public boolean test(ProviderCredentials credentials) {
    LlmResult result = providerDeadline.complete(new LlmRequest(SYSTEM_PROMPT, PROBE_DATA, credentials));
    return result.status() == LlmStatus.OK || result.status() == LlmStatus.REPAIRED;
  }
}
