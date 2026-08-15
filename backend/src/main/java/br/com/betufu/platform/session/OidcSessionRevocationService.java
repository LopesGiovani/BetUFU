package br.com.betufu.platform.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import br.com.betufu.platform.security.OidcRevocationClient;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;

/**
 * Revokes the OIDC refresh token when a BETUFU session is invalidated for an
 * {@link SessionTerminationReason#ADMINISTRATIVE} or {@link SessionTerminationReason#SECURITY}
 * reason.
 *
 * <p>Ordering is the security contract and is deliberate:
 *
 * <ol>
 *   <li>capture the refresh token server-side into a pending entry keyed by the opaque session id;
 *   <li>remove the authorized client, so the local state is terminal <em>before</em> any remote
 *       call is attempted;
 *   <li>only then contact the provider.
 * </ol>
 *
 * <p>A remote failure therefore can never revive a session: by the time the network is touched,
 * the authorized client is already gone. The captured token is <em>retained in the pending
 * entry</em> for {@link #PENDING_TTL}, so a failed attempt can still be retried inside that
 * window — without it the only copy of the token would be destroyed by the very failure that
 * makes revocation necessary, leaving the grant alive at the provider.
 *
 * <p>Replay is bounded by the same TTL and a single attempt is in flight at a time per session,
 * so repetition converges instead of hammering the provider.
 *
 * <p>Idle timeout and current-device logout deliberately do NOT revoke: they end a browser
 * session, not the identity grant.
 */
@Service
@Profile("runtime | integrated")
public class OidcSessionRevocationService {

    /**
     * Bounds <em>reuse</em>: captured material is never replayed once this elapses.
     *
     * <p>It does not bound <em>retention</em>. {@link #purgeExpired()} runs opportunistically, at
     * the start of each invalidation, so an entry whose revocation failed stays in the map until
     * the next {@code ADMINISTRATIVE} or {@code SECURITY} invalidation — which may be far later,
     * or never before a restart. A refresh token that is still live at the provider can therefore
     * outlive this window in memory. Moving the authorized-client store to durable storage would
     * change this design, so scheduled purging is deliberately deferred to that decision rather
     * than built on top of a map that may not survive it.
     */
    static final Duration PENDING_TTL = Duration.ofMinutes(5);

    private final OidcRevocationClient revocationClient;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final Clock clock;
    private final ConcurrentMap<String, PendingRevocation> pendingBySessionId =
            new ConcurrentHashMap<>();

    public OidcSessionRevocationService(
            OidcRevocationClient revocationClient,
            OAuth2AuthorizedClientService authorizedClientService,
            Clock clock) {
        this.revocationClient = Objects.requireNonNull(
                revocationClient, "revocationClient must not be null");
        this.authorizedClientService = Objects.requireNonNull(
                authorizedClientService, "authorizedClientService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Applies the revocation contract for one invalidated session.
     *
     * <p>Never throws on remote failure: the outcome is returned so the caller can record it
     * without the invalidation path itself becoming failure-prone. Silent degradation is still
     * avoided, because every path maps to a distinct, assertable outcome.
     */
    public Outcome revokeOnInvalidation(
            String sessionId,
            String registrationId,
            String principalName,
            SessionTerminationReason reason) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(registrationId, "registrationId must not be null");
        Objects.requireNonNull(principalName, "principalName must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (!revokes(reason)) {
            return Outcome.NOT_APPLICABLE;
        }

        purgeExpired();

        // computeIfAbsent, not get + putIfAbsent: concurrent callers must not each run capture().
        // The loser of a putIfAbsent race could be the one that read the token while the winner
        // read null (its own load happening after a sibling's remove), publishing an entry with
        // no material and losing the only copy of the token. Capture touches the in-memory
        // authorized-client store only, so nothing blocking runs under the mapping function.
        PendingRevocation pending = pendingBySessionId.computeIfAbsent(
                sessionId, key -> capture(registrationId, principalName));

        if (pending.revoked) {
            return Outcome.ALREADY_CLAIMED;
        }
        if (pending.refreshToken == null) {
            return Outcome.NO_REFRESH_TOKEN;
        }
        if (!pending.attemptInFlight.compareAndSet(false, true)) {
            return Outcome.ALREADY_CLAIMED;
        }

        try {
            revocationClient.revokeRefreshToken(pending.registration, pending.refreshToken);
            pending.revoked = true;
            return Outcome.REVOKED;
        } catch (RuntimeException failure) {
            // The claim and its material are intentionally kept: the session stays invalid and a
            // retry remains possible until the TTL elapses.
            return Outcome.REMOTE_FAILURE;
        } finally {
            pending.attemptInFlight.set(false);
        }
    }

    /**
     * Reads the refresh token and makes the local state terminal.
     *
     * <p>The authorized client is removed whether or not a token was present, so the grant can
     * never be reused locally after this point.
     */
    private PendingRevocation capture(String registrationId, String principalName) {
        OAuth2AuthorizedClient authorizedClient =
                authorizedClientService.loadAuthorizedClient(registrationId, principalName);
        String refreshToken = refreshTokenOf(authorizedClient);
        ClientRegistration registration =
                (authorizedClient == null) ? null : authorizedClient.getClientRegistration();

        authorizedClientService.removeAuthorizedClient(registrationId, principalName);

        return new PendingRevocation(clock.instant(), refreshToken, registration);
    }

    private static boolean revokes(SessionTerminationReason reason) {
        return reason == SessionTerminationReason.ADMINISTRATIVE
                || reason == SessionTerminationReason.SECURITY;
    }

    private static String refreshTokenOf(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null || authorizedClient.getRefreshToken() == null) {
            return null;
        }

        String value = authorizedClient.getRefreshToken().getTokenValue();

        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Drops entries past the TTL.
     *
     * <p>Removal is conditional on the exact value observed: an unconditional
     * {@code Iterator.remove()} could delete a fresh claim created by another thread between the
     * read and the removal, letting a second attempt run for a session already claimed.
     */
    private void purgeExpired() {
        Instant deadline = clock.instant().minus(PENDING_TTL);

        for (Map.Entry<String, PendingRevocation> entry : pendingBySessionId.entrySet()) {
            if (entry.getValue().claimedAt.isBefore(deadline)) {
                pendingBySessionId.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Captured material for one session.
     *
     * <p>Unusable once {@link #PENDING_TTL} elapses, but not necessarily gone from memory by
     * then — see the note on that constant. Deliberately a plain class rather than a record: a
     * generated {@code toString()} would print the refresh token, so any accidental log of this
     * map would leak it.
     */
    private static final class PendingRevocation {

        private final Instant claimedAt;
        private final String refreshToken;
        private final ClientRegistration registration;
        private final AtomicBoolean attemptInFlight = new AtomicBoolean();
        private volatile boolean revoked;

        private PendingRevocation(
                Instant claimedAt, String refreshToken, ClientRegistration registration) {
            this.claimedAt = claimedAt;
            this.refreshToken = refreshToken;
            this.registration = registration;
        }
    }

    /** Distinct, assertable result of one invalidation. */
    public enum Outcome {
        /** The reason does not revoke the identity grant. */
        NOT_APPLICABLE,
        /** Already revoked, or another attempt for the same session is in flight. */
        ALREADY_CLAIMED,
        /** The authorized client was removed, but carried no refresh token to revoke. */
        NO_REFRESH_TOKEN,
        /** The provider accepted the revocation. */
        REVOKED,
        /**
         * The provider could not be reached or refused. The session remains invalid and the
         * captured token stays available for retry until the TTL elapses. A caller that observes
         * this outcome should surface it: the grant may still be alive at the provider.
         */
        REMOTE_FAILURE
    }
}
