package br.com.betufu.platform.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Single platform service responsible for terminating all active sessions of an identity and
 * triggering OIDC grant revocation for each invalidated session.
 *
 * <p><strong>Security invariant & ordering:</strong>
 * <ol>
 *   <li>All active sessions of the identity are deleted from the local repository <em>first</em>;
 *   <li>{@link SessionInvalidationListener} is notified for each session independently with the
 *       given termination reason to revoke the provider identity grant;
 *   <li>The returned {@link OidcSessionRevocationService.Outcome} is <em>always inspected</em> and
 *       any unrevoked outcome or local technical failure is captured for caller auditing.
 * </ol>
 *
 * <p>A remote failure never revives a session: the session is already terminated locally.
 */
@Service
@Profile("runtime | integrated")
public class IdentitySessionTerminationService {

    public static final String DEFAULT_REGISTRATION_ID = "keycloak";

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepositoryProvider;
    private final SessionInvalidationListener sessionInvalidationListener;

    public IdentitySessionTerminationService(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepositoryProvider,
            SessionInvalidationListener sessionInvalidationListener) {
        this.sessionRepositoryProvider = Objects.requireNonNull(
                sessionRepositoryProvider, "sessionRepositoryProvider must not be null");
        this.sessionInvalidationListener = Objects.requireNonNull(
                sessionInvalidationListener, "sessionInvalidationListener must not be null");
    }

    /**
     * Terminates all sessions associated with {@code principalName} using the default registration
     * ID ("keycloak").
     *
     * @param principalName the principal name (subject identifier)
     * @param reason the reason for session termination
     * @return a {@link TerminationSummary} detailing total terminated sessions and unrevoked outcomes
     */
    public TerminationSummary terminateAllSessionsFor(
            String principalName, SessionTerminationReason reason) {
        return terminateAllSessionsFor(principalName, DEFAULT_REGISTRATION_ID, reason);
    }

    /**
     * Terminates all sessions associated with {@code principalName} for the given registration ID.
     *
     * @param principalName the principal name (subject identifier)
     * @param registrationId the OIDC client registration identifier
     * @param reason the reason for session termination
     * @return a {@link TerminationSummary} detailing total terminated sessions and unrevoked outcomes
     */
    public TerminationSummary terminateAllSessionsFor(
            String principalName, String registrationId, SessionTerminationReason reason) {
        Objects.requireNonNull(principalName, "principalName must not be null");
        if (principalName.isBlank()) {
            throw new IllegalArgumentException("principalName must not be blank");
        }
        Objects.requireNonNull(registrationId, "registrationId must not be null");
        if (registrationId.isBlank()) {
            throw new IllegalArgumentException("registrationId must not be blank");
        }
        Objects.requireNonNull(reason, "reason must not be null");

        FindByIndexNameSessionRepository<? extends Session> sessionRepository =
                sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository == null) {
            throw new IllegalStateException(
                    "Indexed session repository is not available in the current context");
        }

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(principalName);
        if (sessions == null || sessions.isEmpty()) {
            return new TerminationSummary(0, List.of(), List.of());
        }

        Set<String> sessionIds = new LinkedHashSet<>();
        for (Map.Entry<String, ? extends Session> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            if (sessionId != null && !sessionId.isBlank()) {
                sessionIds.add(sessionId);
            }
        }
        if (sessionIds.isEmpty()) {
            return new TerminationSummary(0, List.of(), List.of());
        }

        // 1. Primary guarantee: Attempt to delete ALL local sessions FIRST before any OIDC revocation
        Set<String> successfulDeletions = new LinkedHashSet<>();
        List<OidcSessionRevocationService.Outcome> unrevokedOutcomes = new ArrayList<>();
        List<RevocationFailure> revocationFailures = new ArrayList<>();

        for (String sessionId : sessionIds) {
            try {
                sessionRepository.deleteById(sessionId);
                successfulDeletions.add(sessionId);
            } catch (RuntimeException ex) {
                revocationFailures.add(new RevocationFailure(
                        TechnicalFailure.LOCAL_DELETION_FAILURE, sessionId));
            }
        }

        // 2. Process OIDC revocation ONLY for sessions that were successfully deleted locally
        for (String sessionId : successfulDeletions) {
            try {
                SessionInvalidationListener.SessionInvalidation invalidation =
                        new SessionInvalidationListener.SessionInvalidation(
                                sessionId, registrationId, principalName, reason);
                OidcSessionRevocationService.Outcome outcome =
                        sessionInvalidationListener.onSessionInvalidated(invalidation);

                // 3. Inspect outcome — never discard
                if (outcome != OidcSessionRevocationService.Outcome.REVOKED) {
                    unrevokedOutcomes.add(outcome);
                }
            } catch (RuntimeException failure) {
                // Local runtime exceptions in listener MUST NOT be mapped to REMOTE_FAILURE.
                // Model explicit technical failure without storing Throwable, principal, token or cookie.
                revocationFailures.add(new RevocationFailure(
                        TechnicalFailure.LOCAL_LISTENER_FAILURE, sessionId));
            }
        }

        return new TerminationSummary(
                successfulDeletions.size(),
                Collections.unmodifiableList(unrevokedOutcomes),
                Collections.unmodifiableList(revocationFailures));
    }

    /**
     * Immutable summary of the identity session termination operation.
     *
     * @param totalTerminated total count of sessions deleted from the repository
     * @param unrevokedOutcomes list of outcomes that were not successfully revoked
     * @param revocationFailures list of local technical failures encountered during revocation
     */
    public record TerminationSummary(
            int totalTerminated,
            List<OidcSessionRevocationService.Outcome> unrevokedOutcomes,
            List<RevocationFailure> revocationFailures) {

        public TerminationSummary(
                int totalTerminated,
                List<OidcSessionRevocationService.Outcome> unrevokedOutcomes) {
            this(totalTerminated, unrevokedOutcomes, List.of());
        }

        public TerminationSummary {
            Objects.requireNonNull(unrevokedOutcomes, "unrevokedOutcomes must not be null");
            Objects.requireNonNull(revocationFailures, "revocationFailures must not be null");
            unrevokedOutcomes = List.copyOf(unrevokedOutcomes);
            revocationFailures = List.copyOf(revocationFailures);
        }

        public boolean isFullyRevoked() {
            return unrevokedOutcomes.isEmpty() && revocationFailures.isEmpty();
        }

        public boolean hasFailures() {
            return !revocationFailures.isEmpty();
        }
    }

    /**
     * Technical classification of revocation failure without sensitive attributes.
     */
    public enum TechnicalFailure {
        LOCAL_DELETION_FAILURE,
        LOCAL_LISTENER_FAILURE
    }

    /**
     * Explicit, auditable representation of a local technical revocation failure.
     */
    public record RevocationFailure(
            TechnicalFailure classification,
            String sessionId) {

        public RevocationFailure {
            Objects.requireNonNull(classification, "classification must not be null");
        }

        public RevocationFailure(TechnicalFailure classification) {
            this(classification, null);
        }

        @Override
        public String toString() {
            return "RevocationFailure[classification=" + classification + "]";
        }
    }
}
