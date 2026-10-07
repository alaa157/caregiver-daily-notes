package com.caregiver.auth;

import java.util.UUID;

/** Verified caregiver identity installed as the authentication principal. */
public record CaregiverPrincipal(UUID id) {
}
