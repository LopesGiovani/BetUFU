package br.com.betufu.verification;

import java.util.Objects;
import java.util.UUID;

import br.com.betufu.sports.EnrollmentFingerprintPort;

/**
 * Public post-commit application event published when an enrollment is successfully verified/registered.
 *
 * <p>Carries only the opaque {@code identityId} to ensure strict privacy isolation (D-71).
 */
public record EnrollmentVerified(UUID identityId) implements EnrollmentFingerprintPort.EnrollmentVerifiedEvent {
    public EnrollmentVerified {
        Objects.requireNonNull(identityId, "identityId must not be null");
    }
}
