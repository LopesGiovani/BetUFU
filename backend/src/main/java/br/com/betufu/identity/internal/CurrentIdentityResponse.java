package br.com.betufu.identity.internal;

import java.time.Instant;
import java.util.UUID;

public record CurrentIdentityResponse(
        UUID identityId,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
