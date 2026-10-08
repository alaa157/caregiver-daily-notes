package com.caregiver.preview;

import java.util.List;

public record AppendVersionRequest(String status, List<String> items) {
}
