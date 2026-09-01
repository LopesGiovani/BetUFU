package br.com.betufu.platform.session;

import br.com.betufu.platform.security.OidcRevocationClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientId;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Revokes the OIDC refresh token when a BETUFU session is invalidated for an {@link
 * SessionTerminationReason#ADMINISTRATIVE} or {@link SessionTerminationReason#SECURITY} reason.
 *
 * <p>Ordering is the security contract and is deliberate:
 *
 * <ol>
 *   <li>capture every authorized-client generation observed under the logical grant ({@code
 *       registrationId + principalName});
 *   <li>remove that authorized client, making the local state terminal before network I/O;
 *   <li>contact the provider with at most one concurrent attempt for that logical grant.
 * </ol>
 *
 * <p>A new authorized client is a new generation even when an older generation was already revoked
 * or failed. Failed material remains retryable for {@link #PENDING_TTL}. After that window the
 * token and client registration are erased, while a non-sensitive failure tombstone remains so
 * expiration can never be mistaken for successful revocation.
 *
 * <p>HTTP timeouts are necessarily at-least-once: the provider may have accepted a request whose
 * response was lost. This service prevents concurrent duplicates per grant and never repeats a
 * generation after an observed success, but it cannot manufacture exactly-once semantics over an
 * ambiguous network timeout.
 *
 * <p>Idle timeout and current-device logout deliberately do not revoke: they end a browser session,
 * not the identity grant.
 */
@Service
@EnableScheduling
@Import(OidcSessionRevocationService.AtomicAuthorizedClientServiceConfiguration.class)
@Profile("runtime | integrated")
public class OidcSessionRevocationService {

  /** Maximum time sensitive failed material remains available for retry. */
  static final Duration PENDING_TTL = Duration.ofMinutes(5);

  private final OidcRevocationClient revocationClient;
  private final AtomicOAuth2AuthorizedClientService authorizedClientService;
  private final Clock clock;
  private final ConcurrentMap<GrantKey, GrantState> stateByGrant = new ConcurrentHashMap<>();

  public OidcSessionRevocationService(
      OidcRevocationClient revocationClient,
      AtomicOAuth2AuthorizedClientService authorizedClientService,
      Clock clock) {
    this.revocationClient =
        Objects.requireNonNull(revocationClient, "revocationClient must not be null");
    this.authorizedClientService =
        Objects.requireNonNull(authorizedClientService, "authorizedClientService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Applies the revocation contract for one invalidated session.
   *
   * <p>Remote failure is returned as an explicit outcome instead of being thrown. Local
   * authorized-client failures may still throw so the caller can classify them separately.
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

    GrantKey grantKey = new GrantKey(registrationId, principalName);
    AttemptBatch[] claimed = new AttemptBatch[1];
    GrantState grantState =
        stateByGrant.compute(
            grantKey,
            (key, existing) -> {
              GrantState current = (existing == null) ? new GrantState() : existing;
              synchronized (current) {
                captureCurrentGeneration(key, current);
                claimed[0] = claimAttempt(current);
              }
              return current;
            });

    return executeAttempt(grantState, claimed[0]);
  }

  /**
   * Retries unresolved generations after every local session has already disappeared.
   *
   * <p>This method never reloads the authorized-client store and never invents a generation. Empty
   * means only that no pending state has ever existed or every known generation was resolved. In
   * particular, an expired remote failure remains {@link Outcome#REMOTE_FAILURE}.
   */
  public Optional<Outcome> retryPendingGrant(
      String registrationId, String principalName, SessionTerminationReason reason) {
    Objects.requireNonNull(registrationId, "registrationId must not be null");
    Objects.requireNonNull(principalName, "principalName must not be null");
    Objects.requireNonNull(reason, "reason must not be null");

    if (!revokes(reason)) {
      return Optional.empty();
    }

    purgeExpired();

    GrantState grantState = stateByGrant.get(new GrantKey(registrationId, principalName));
    if (grantState == null) {
      return Optional.empty();
    }

    AttemptBatch attemptBatch;
    synchronized (grantState) {
      if (grantState.generations.isEmpty() || allResolved(grantState)) {
        return Optional.empty();
      }
      attemptBatch = claimAttempt(grantState);
    }

    return Optional.of(executeAttempt(grantState, attemptBatch));
  }

  /** Atomically removes and returns the current client before any remote request is made. */
  private void captureCurrentGeneration(GrantKey grantKey, GrantState grantState) {
    OAuth2AuthorizedClient authorizedClient =
        authorizedClientService.takeAuthorizedClient(
            grantKey.registrationId(), grantKey.principalName());

    if (authorizedClient != null) {
      grantState.generations.add(
          new RevocationGeneration(
              clock.instant(),
              refreshTokenOf(authorizedClient),
              authorizedClient.getClientRegistration()));
    } else if (grantState.generations.isEmpty()) {
      grantState.generations.add(RevocationGeneration.withoutRefreshToken(clock.instant()));
    }
  }

  private AttemptBatch claimAttempt(GrantState grantState) {
    if (grantState.attemptInFlight) {
      return AttemptBatch.completed(Outcome.ALREADY_CLAIMED);
    }

    List<RevocationGeneration> retryable =
        grantState.generations.stream().filter(RevocationGeneration::isRetryable).toList();
    if (retryable.isEmpty()) {
      if (allResolved(grantState)) {
        return AttemptBatch.completed(Outcome.ALREADY_CLAIMED);
      }
      return AttemptBatch.completed(aggregateOutcome(grantState));
    }

    grantState.attemptInFlight = true;
    return AttemptBatch.claimed(retryable);
  }

  private Outcome executeAttempt(GrantState grantState, AttemptBatch attemptBatch) {
    if (attemptBatch.completedOutcome() != null) {
      return attemptBatch.completedOutcome();
    }

    try {
      for (RevocationGeneration generation : attemptBatch.generations()) {
        try {
          revocationClient.revokeRefreshToken(generation.registration, generation.refreshToken);
          synchronized (grantState) {
            generation.markRevoked();
          }
        } catch (RuntimeException failure) {
          synchronized (grantState) {
            generation.markRemoteFailure();
          }
        }
      }
    } finally {
      synchronized (grantState) {
        grantState.attemptInFlight = false;
      }
    }

    synchronized (grantState) {
      return aggregateOutcome(grantState);
    }
  }

  private static boolean allResolved(GrantState grantState) {
    return !grantState.generations.isEmpty()
        && grantState.generations.stream().allMatch(RevocationGeneration::isResolved);
  }

  private static Outcome aggregateOutcome(GrantState grantState) {
    if (grantState.generations.stream().anyMatch(RevocationGeneration::isRemoteFailure)) {
      return Outcome.REMOTE_FAILURE;
    }
    if (grantState.generations.stream().anyMatch(RevocationGeneration::isWithoutRefreshToken)) {
      return Outcome.NO_REFRESH_TOKEN;
    }
    if (grantState.generations.stream().anyMatch(RevocationGeneration::isPending)) {
      return Outcome.REMOTE_FAILURE;
    }
    return Outcome.REVOKED;
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
   * Opportunistically erases expired secret material across all grants. A state with an active HTTP
   * attempt is skipped; that request began inside the TTL and its finally block releases the
   * single-flight guard.
   */
  @Scheduled(
      fixedDelayString = "${betufu.session.oidc-revocation.cleanup-interval:PT1M}",
      initialDelayString = "${betufu.session.oidc-revocation.cleanup-initial-delay:PT1M}")
  void purgeExpired() {
    Instant deadline = clock.instant().minus(PENDING_TTL);

    for (GrantKey grantKey : List.copyOf(stateByGrant.keySet())) {
      stateByGrant.computeIfPresent(
          grantKey,
          (ignored, grantState) -> {
            synchronized (grantState) {
              if (grantState.attemptInFlight) {
                return grantState;
              }
              for (RevocationGeneration generation : grantState.generations) {
                generation.expireSensitiveMaterialBefore(deadline);
              }
              grantState.generations.removeIf(generation -> generation.isResolvedBefore(deadline));
              return grantState.generations.isEmpty() ? null : grantState;
            }
          });
    }
  }

  int retainedSensitiveGenerationCount() {
    int retained = 0;
    for (GrantState grantState : stateByGrant.values()) {
      synchronized (grantState) {
        for (RevocationGeneration generation : grantState.generations) {
          if (generation.hasSensitiveMaterial()) {
            retained += 1;
          }
        }
      }
    }
    return retained;
  }

  /** State for every opaque generation observed under one logical grant. */
  private static final class GrantState {

    private final List<RevocationGeneration> generations = new ArrayList<>();
    private boolean attemptInFlight;
  }

  /**
   * One observed authorized-client generation. This is deliberately not a record: a generated
   * {@code toString()} could expose the refresh token or client registration.
   */
  private static final class RevocationGeneration {

    private enum Status {
      PENDING,
      REMOTE_FAILURE,
      EXPIRED_REMOTE_FAILURE,
      NO_REFRESH_TOKEN,
      REVOKED
    }

    private final Instant claimedAt;
    private String refreshToken;
    private ClientRegistration registration;
    private Status status;

    private RevocationGeneration(
        Instant claimedAt, String refreshToken, ClientRegistration registration) {
      this.claimedAt = claimedAt;
      this.refreshToken = refreshToken;
      this.registration = (refreshToken == null) ? null : registration;
      this.status =
          (refreshToken == null || registration == null) ? Status.NO_REFRESH_TOKEN : Status.PENDING;
    }

    private static RevocationGeneration withoutRefreshToken(Instant claimedAt) {
      return new RevocationGeneration(claimedAt, null, null);
    }

    private boolean isRetryable() {
      return (status == Status.PENDING || status == Status.REMOTE_FAILURE)
          && refreshToken != null
          && registration != null;
    }

    private boolean isResolved() {
      return status == Status.REVOKED;
    }

    private boolean isRemoteFailure() {
      return status == Status.REMOTE_FAILURE || status == Status.EXPIRED_REMOTE_FAILURE;
    }

    private boolean isWithoutRefreshToken() {
      return status == Status.NO_REFRESH_TOKEN;
    }

    private boolean isPending() {
      return status == Status.PENDING;
    }

    private boolean isResolvedBefore(Instant deadline) {
      return status == Status.REVOKED && claimedAt.isBefore(deadline);
    }

    private boolean hasSensitiveMaterial() {
      return refreshToken != null || registration != null;
    }

    private void markRevoked() {
      status = Status.REVOKED;
      eraseSensitiveMaterial();
    }

    private void markRemoteFailure() {
      status = Status.REMOTE_FAILURE;
    }

    private void expireSensitiveMaterialBefore(Instant deadline) {
      if (!claimedAt.isBefore(deadline)) {
        return;
      }
      if (status == Status.PENDING || status == Status.REMOTE_FAILURE) {
        status = Status.EXPIRED_REMOTE_FAILURE;
        eraseSensitiveMaterial();
      } else if (status == Status.REVOKED) {
        eraseSensitiveMaterial();
      }
    }

    private void eraseSensitiveMaterial() {
      refreshToken = null;
      registration = null;
    }
  }

  private record AttemptBatch(List<RevocationGeneration> generations, Outcome completedOutcome) {

    private static AttemptBatch claimed(List<RevocationGeneration> generations) {
      return new AttemptBatch(List.copyOf(generations), null);
    }

    private static AttemptBatch completed(Outcome outcome) {
      return new AttemptBatch(List.of(), outcome);
    }
  }

  /** Logical identity of an authorized-client grant; local session id is intentionally absent. */
  private record GrantKey(String registrationId, String principalName) {

    @Override
    public String toString() {
      return "GrantKey[redacted]";
    }
  }

  /** Distinct, assertable result of one invalidation or retry. */
  public enum Outcome {
    /** The reason does not revoke the identity grant. */
    NOT_APPLICABLE,
    /** The known grant is resolved, or another attempt for it is in flight. */
    ALREADY_CLAIMED,
    /** The authorized client was removed but carried no refresh token. */
    NO_REFRESH_TOKEN,
    /** Every known retryable generation was accepted by the provider. */
    REVOKED,
    /** At least one generation remains unresolved, including an expired failure tombstone. */
    REMOTE_FAILURE
  }

  /**
   * Publishes the application-owned store before Boot considers its conditional in-memory default.
   * Every OAuth2 login, logout and administrative revocation therefore observes the same map and
   * the same atomic take operation.
   */
  @Configuration(proxyBeanMethods = false)
  @Profile("runtime | integrated")
  static class AtomicAuthorizedClientServiceConfiguration {

    @Bean(name = "authorizedClientService")
    AtomicOAuth2AuthorizedClientService authorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository) {
      return new AtomicOAuth2AuthorizedClientService(clientRegistrationRepository);
    }
  }

  /**
   * In-memory OAuth2 authorized-client store with an atomic remove-and-return primitive.
   *
   * <p>The standard Spring interface deliberately exposes load and remove separately. Calling those
   * methods in sequence can erase a new login saved between them. This implementation keeps the
   * official interface for the Spring Security pipeline and adds {@link
   * #takeAuthorizedClient(String, String)} for the revocation path.
   */
  static final class AtomicOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final ConcurrentMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient> authorizedClients;

    AtomicOAuth2AuthorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
      this(clientRegistrationRepository, new ConcurrentHashMap<>());
    }

    AtomicOAuth2AuthorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository,
        ConcurrentMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient> authorizedClients) {
      this.clientRegistrationRepository =
          Objects.requireNonNull(
              clientRegistrationRepository, "clientRegistrationRepository must not be null");
      this.authorizedClients =
          Objects.requireNonNull(authorizedClients, "authorizedClients must not be null");
    }

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
        String clientRegistrationId, String principalName) {
      validateKey(clientRegistrationId, principalName);
      ClientRegistration registration =
          clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
      if (registration == null) {
        return null;
      }
      OAuth2AuthorizedClient stored =
          authorizedClients.get(new OAuth2AuthorizedClientId(clientRegistrationId, principalName));
      return materialize(registration, stored);
    }

    @Override
    public void saveAuthorizedClient(
        OAuth2AuthorizedClient authorizedClient,
        org.springframework.security.core.Authentication principal) {
      Assert.notNull(authorizedClient, "authorizedClient cannot be null");
      Assert.notNull(principal, "principal cannot be null");
      authorizedClients.put(
          new OAuth2AuthorizedClientId(
              authorizedClient.getClientRegistration().getRegistrationId(), principal.getName()),
          authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
      validateKey(clientRegistrationId, principalName);
      if (clientRegistrationRepository.findByRegistrationId(clientRegistrationId) == null) {
        return;
      }
      authorizedClients.remove(new OAuth2AuthorizedClientId(clientRegistrationId, principalName));
    }

    OAuth2AuthorizedClient takeAuthorizedClient(String clientRegistrationId, String principalName) {
      validateKey(clientRegistrationId, principalName);
      ClientRegistration registration =
          clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
      if (registration == null) {
        return null;
      }
      OAuth2AuthorizedClient removed =
          authorizedClients.remove(
              new OAuth2AuthorizedClientId(clientRegistrationId, principalName));
      return materialize(registration, removed);
    }

    @SuppressWarnings("unchecked")
    private <T extends OAuth2AuthorizedClient> T materialize(
        ClientRegistration registration, OAuth2AuthorizedClient stored) {
      if (stored == null) {
        return null;
      }
      return (T)
          new OAuth2AuthorizedClient(
              registration,
              stored.getPrincipalName(),
              stored.getAccessToken(),
              stored.getRefreshToken());
    }

    private static void validateKey(String clientRegistrationId, String principalName) {
      Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
      Assert.hasText(principalName, "principalName cannot be empty");
    }
  }
}
