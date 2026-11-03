package com.caregiver.notes;

import java.util.List;
import java.util.Map;

public record CorrectionHistoryResponse(NoteResponse originalNote,
    List<NoteCorrectionEventResponse> events, Map<String, EffectiveObservation> effectiveObservations) { }
