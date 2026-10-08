package com.caregiver.preview;

public record SignalDto(
    String noteId,
    String recipientId,
    String date,
    boolean fallReported,
    Integer pain,
    String medication,
    boolean medicationUnverified,
    boolean poorAppetite,
    String text) {
}
