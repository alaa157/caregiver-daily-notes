package com.caregiver.notes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CorrectionRequest(@NotEmpty List<@Valid FieldCorrectionRequest> corrections) { }
