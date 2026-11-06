package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe connection-test result; never contains credentials or provider text. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ByokConnectionTestResponse(
    boolean success,
    String providerId,
    String model,
    String errorCode) {
}
