package br.com.betufu.sports.internal.athlete;

import java.util.Objects;
import java.util.UUID;

/**
 * Post-commit application event published when an athlete is created.
 *
 * <p>Carries only the opaque {@code athleteId} (D-71).
 */
public record SportsAthleteCreated(UUID athleteId) {
    public SportsAthleteCreated {
        Objects.requireNonNull(athleteId, "athleteId must not be null");
    }
}
