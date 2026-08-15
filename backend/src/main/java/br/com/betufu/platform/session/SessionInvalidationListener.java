package br.com.betufu.platform.session;

import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seam between BETUFU session invalidation and OIDC revocation.
 *
 * <p>Invalidation reaches this listener as an explicit, fully described event rather than as a
 * generic container callback: the termination reason decides whether the identity grant must be
 * revoked, and a servlet or Spring Session event does not carry that reason. Plan 01-22 wires the
 * concrete administrative and security triggers; this class fixes the contract they call.
 *
 * <p>The listener holds no policy of its own beyond validating the event — the ordering and
 * idempotency guarantees live in {@link OidcSessionRevocationService}.
 *
 * <p><strong>Caller obligation.</strong> This listener terminates the identity <em>grant</em>; it
 * does NOT invalidate the {@code HttpSession}. The caller that raises the event owns that, and
 * must invalidate the session itself — otherwise the grant is revoked while the browser session
 * survives. Do not assume this class already did it.
 *
 * <p><strong>Caller obligation.</strong> The returned {@link OidcSessionRevocationService.Outcome}
 * must be inspected. {@code REMOTE_FAILURE} and {@code NO_REFRESH_TOKEN} both mean the grant may
 * still be alive at the provider, and neither is a success; discarding the outcome turns a failed
 * revocation into a silent one.
 */
@Component
@Profile("runtime | integrated")
public class SessionInvalidationListener {

    private final OidcSessionRevocationService revocationService;

    public SessionInvalidationListener(OidcSessionRevocationService revocationService) {
        this.revocationService = Objects.requireNonNull(
                revocationService, "revocationService must not be null");
    }

    public OidcSessionRevocationService.Outcome onSessionInvalidated(
            SessionInvalidation invalidation) {
        Objects.requireNonNull(invalidation, "invalidation must not be null");

        return revocationService.revokeOnInvalidation(
                invalidation.sessionId(),
                invalidation.registrationId(),
                invalidation.principalName(),
                invalidation.reason());
    }

    /**
     * One invalidated session.
     *
     * <p>{@code sessionId} is the opaque BETUFU session identifier and is used only as the
     * single-flight key — it is never sent to the provider.
     */
    public record SessionInvalidation(
            String sessionId,
            String registrationId,
            String principalName,
            SessionTerminationReason reason) {

        public SessionInvalidation {
            requireText(sessionId, "sessionId");
            requireText(registrationId, "registrationId");
            requireText(principalName, "principalName");
            Objects.requireNonNull(reason, "reason must not be null");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
