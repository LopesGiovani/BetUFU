package br.com.betufu.platform.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record SessionContractDto(
        boolean authenticated,
        String displayName,
        Duration idleTimeout,
        Duration absoluteTimeout,
        Instant lastValidActivityAt,
        boolean mfaAuthenticated) {

    public SessionContractDto {
        idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
        if (!SessionPolicy.IDLE_TIMEOUT.equals(idleTimeout)) {
            throw new IllegalArgumentException("idleTimeout must be PT8H");
        }
        if (absoluteTimeout != null) {
            throw new IllegalArgumentException("absoluteTimeout must remain null in the MVP");
        }
        if (authenticated) {
            if (displayName != null && displayName.isBlank()) {
                throw new IllegalArgumentException(
                        "displayName must not be blank when present");
            }
            lastValidActivityAt = Objects.requireNonNull(
                    lastValidActivityAt,
                    "lastValidActivityAt must not be null for an authenticated session");
        } else if (displayName != null || lastValidActivityAt != null || mfaAuthenticated) {
            throw new IllegalArgumentException(
                    "an unauthenticated contract must not expose session details");
        }
    }

    public static SessionContractDto authenticated(
            String displayName,
            Instant lastValidActivityAt,
            boolean mfaAuthenticated) {
        return new SessionContractDto(
                true,
                displayName,
                SessionPolicy.IDLE_TIMEOUT,
                null,
                lastValidActivityAt,
                mfaAuthenticated);
    }

    public static SessionContractDto unauthenticated() {
        return new SessionContractDto(
                false,
                null,
                SessionPolicy.IDLE_TIMEOUT,
                null,
                null,
                false);
    }
}
