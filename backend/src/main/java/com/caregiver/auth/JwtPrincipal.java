package com.caregiver.auth;

import java.util.UUID;

/** Identity verified from a JWT: the caregiver id only, no profile data. */
public record JwtPrincipal(UUID caregiverId) {
}
