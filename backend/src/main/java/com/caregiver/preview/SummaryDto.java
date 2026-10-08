package com.caregiver.preview;

import java.util.List;

public record SummaryDto(
    String id,
    String recipientId,
    int periodDays,
    String text,
    List<String> redFlags,
    List<EvidenceDto> evidence,
    List<UncertaintyDto> uncertainties) {

  public record EvidenceDto(String noteId, String quote) {
  }

  public record UncertaintyDto(String topic, String detail) {
  }
}
