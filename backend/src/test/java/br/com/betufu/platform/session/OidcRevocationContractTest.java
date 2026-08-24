package br.com.betufu.platform.session;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientId;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import br.com.betufu.platform.security.OidcRevocationClient;

/**
 * Contract for discovery-driven, redacted, idempotent OIDC refresh-token revocation.
 *
 * <p>The provider is a loopback fixture that records every request it receives, so the assertions
 * cover both what was sent and — just as importantly — what was never sent.
 */
class OidcRevocationContractTest {

    private static final String REGISTRATION_ID = "keycloak";
    private static final String CLIENT_ID = "betufu-bff";
    private static final String CLIENT_SECRET = "revocation-contract-secret";
    private static final String PRINCIPAL = "revocation-principal";
    private static final String REFRESH_TOKEN = "refresh-token-value-must-never-leak";

    private static final Duration HANG_DURATION = Duration.ofSeconds(10);
    private static final Duration IMPATIENT_READ_TIMEOUT = Duration.ofMillis(250);

    private static final List<RecordedRequest> REQUESTS = new ArrayList<>();
    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(200);
    private static final ExecutorService PROVIDER_EXECUTOR = Executors.newCachedThreadPool();
    private static final HttpServer PROVIDER = startProvider();
    private static final String REVOCATION_URI =
            "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/protocol/openid-connect/revoke";

    private MutableClock clock;
    private OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService
            authorizedClientService;
    private OidcSessionRevocationService revocationService;
    private SessionInvalidationListener listener;

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/protocol/openid-connect/revoke", exchange -> {
                String body = new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                synchronized (REQUESTS) {
                    REQUESTS.add(new RecordedRequest(
                            exchange.getRequestURI().getPath(),
                            exchange.getRequestMethod(),
                            exchange.getRequestHeaders().getFirst("Content-Type"),
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            body));
                }
                byte[] response =
                        "{\"error\":\"invalid_request\",\"leaked\":\"remote-body-must-not-surface\"}"
                                .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(RESPONSE_STATUS.get(), response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            });
            // A never-answering endpoint, to exercise the client timeout. It runs on a pooled
            // executor so the sleeping handler cannot stall the /revoke endpoint used by the
            // other tests.
            server.createContext("/protocol/openid-connect/hang", exchange -> {
                try {
                    Thread.sleep(HANG_DURATION.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(PROVIDER_EXECUTOR);
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not start the fixture provider", failure);
        }
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
        PROVIDER_EXECUTOR.shutdownNow();
    }

    @BeforeEach
    void prepare() {
        synchronized (REQUESTS) {
            REQUESTS.clear();
        }
        RESPONSE_STATUS.set(200);
        clock = new MutableClock(Instant.parse("2026-08-14T12:00:00Z"));

        ClientRegistration registration = registration(REVOCATION_URI);
        authorizedClientService = new OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(registration));
        revocationService = new OidcSessionRevocationService(
                new OidcRevocationClient(RestClient.builder().build()),
                authorizedClientService,
                clock);
        listener = new SessionInvalidationListener(revocationService);
    }

    @Test
    void resolvesTheEndpointFromDiscoveryAndSendsOnlyTheRefreshTokenAndHint() {
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-a", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);

        RecordedRequest request = singleRequest();
        assertThat(request.path()).isEqualTo("/protocol/openid-connect/revoke");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.contentType()).startsWith("application/x-www-form-urlencoded");

        Map<String, String> form = request.form();
        assertThat(form)
                .containsEntry("token", REFRESH_TOKEN)
                .containsEntry("token_type_hint", "refresh_token")
                .hasSize(2);
    }

    @Test
    void authenticatesAsTheConfidentialClientAndNeverFallsBackToTheAccessToken() {
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        invalidate("session-b", SessionTerminationReason.SECURITY);

        RecordedRequest request = singleRequest();
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        assertThat(request.authorization()).isEqualTo(expected);
        assertThat(request.body()).doesNotContain("access-token-value");
    }

    @Test
    void failsClosedWhenDiscoveryDoesNotPublishTheEndpoint() {
        ClientRegistration withoutRevocation = registration(null);
        authorizedClientService = new OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(withoutRevocation));
        revocationService = new OidcSessionRevocationService(
                new OidcRevocationClient(RestClient.builder().build()),
                authorizedClientService,
                clock);
        listener = new SessionInvalidationListener(revocationService);
        saveAuthorizedClient(withoutRevocation, REFRESH_TOKEN);

        assertThat(invalidate("session-c", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(REQUESTS).isEmpty();
        assertThat(storedClient())
                .isNull();
    }

    @Test
    void redactsTheTokenAndTheRemoteBodyFromFailures() {
        RESPONSE_STATUS.set(400);
        ClientRegistration registration = registration(REVOCATION_URI);

        assertThatThrownBy(() -> new OidcRevocationClient(RestClient.builder().build())
                        .revokeRefreshToken(registration, REFRESH_TOKEN))
                .isInstanceOf(OidcRevocationClient.OidcRevocationException.class)
                .hasMessageContaining("400")
                .hasMessageNotContaining(REFRESH_TOKEN)
                .hasMessageNotContaining("remote-body-must-not-surface")
                .hasMessageNotContaining(CLIENT_SECRET);
    }

    @Test
    void remoteFailureLeavesTheLocalStateTerminal() {
        RESPONSE_STATUS.set(500);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-d", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(storedClient())
                .isNull();
    }

    /**
     * The material captured before the failure must survive it.
     *
     * <p>If the refresh token were discarded when the provider is unreachable, the only copy
     * would be destroyed by the very failure that makes revocation necessary: the session would
     * end locally while the grant stayed alive at the provider, with no possible retry.
     */
    @Test
    void aFailedRevocationIsRetriableFromAnotherSessionOfTheSameGrant() {
        RESPONSE_STATUS.set(500);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-j", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(storedClient()).as("local state stays terminal").isNull();

        // Deliberately NOT re-saving the authorized client: the retry may only rely on the
        // material held in the pending entry.
        RESPONSE_STATUS.set(200);
        assertThat(invalidate("session-j-sibling", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);

        synchronized (REQUESTS) {
            assertThat(REQUESTS).hasSize(2);
            assertThat(REQUESTS.get(1).form()).containsEntry("token", REFRESH_TOKEN);
        }
    }

    @Test
    void explicitGrantRetryUsesRetainedMaterialAfterLocalSessionsDisappear() {
        RESPONSE_STATUS.set(500);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-explicit-retry", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(storedClient()).as("local state stays terminal").isNull();

        RESPONSE_STATUS.set(200);
        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.IDLE_TIMEOUT))
                .as("a non-revoking reason must not consume retained material")
                .isEmpty();
        assertThat(REQUESTS).hasSize(1);

        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                .contains(OidcSessionRevocationService.Outcome.REVOKED);
        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                .as("completed material is no longer pending")
                .isEmpty();

        synchronized (REQUESTS) {
            assertThat(REQUESTS).hasSize(2);
            assertThat(REQUESTS.get(1).form()).containsEntry("token", REFRESH_TOKEN);
        }
    }

    @Test
    void explicitGrantRetryWithoutPendingMaterialIsEmpty() {
        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                .isEmpty();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void explicitGrantRetryWithoutRefreshTokenRemainsNonTerminal() {
        saveAuthorizedClient(registration(REVOCATION_URI), null);

        assertThat(invalidate("session-without-refresh", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.NO_REFRESH_TOKEN);
        assertThat(revocationService.retainedSensitiveGenerationCount())
                .as("a generation without refresh token must not retain the client secret")
                .isZero();
        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                .contains(OidcSessionRevocationService.Outcome.NO_REFRESH_TOKEN);
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void expiredRemoteFailureRemainsNonTerminalWithoutReplayingSecretMaterial() {
        RESPONSE_STATUS.set(500);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-k", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(revocationService.retainedSensitiveGenerationCount()).isEqualTo(1);

        clock.advance(OidcSessionRevocationService.PENDING_TTL.plusSeconds(1));
        RESPONSE_STATUS.set(200);
        revocationService.purgeExpired();

        assertThat(revocationService.retainedSensitiveGenerationCount())
                .as("the scheduled cleanup erases secrets without needing revoke or retry traffic")
                .isZero();

        assertThat(revocationService.retryPendingGrant(
                        REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                .as("expiration must erase the secret without erasing failure evidence")
                .contains(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(invalidate("session-k", SessionTerminationReason.SECURITY))
                .as("a later invalidation must not convert the tombstone into false success")
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void secretCleanupIsScheduledIndependentlyOfRevocationTraffic() throws Exception {
        Scheduled schedule = OidcSessionRevocationService.class
                .getDeclaredMethod("purgeExpired")
                .getAnnotation(Scheduled.class);

        assertThat(schedule).isNotNull();
        assertThat(schedule.fixedDelayString())
                .isEqualTo("${betufu.session.oidc-revocation.cleanup-interval:PT1M}");
        assertThat(schedule.initialDelayString())
                .isEqualTo("${betufu.session.oidc-revocation.cleanup-initial-delay:PT1M}");
    }

    @Test
    void aNewAuthorizedClientAfterSuccessIsASeparateGenerationInsideTheTtl() {
        String nextRefreshToken = "second-refresh-token-must-never-leak";
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-generation-1", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);

        saveAuthorizedClient(registration(REVOCATION_URI), nextRefreshToken);
        assertThat(invalidate("session-generation-2", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);
        assertThat(storedClient()).isNull();

        synchronized (REQUESTS) {
            assertThat(REQUESTS).hasSize(2);
            assertThat(REQUESTS.get(0).form()).containsEntry("token", REFRESH_TOKEN);
            assertThat(REQUESTS.get(1).form()).containsEntry("token", nextRefreshToken);
        }
    }

    @Test
    void aNewAuthorizedClientAfterFailurePreservesAndProcessesBothGenerations() {
        String nextRefreshToken = "second-refresh-token-must-never-leak";
        RESPONSE_STATUS.set(500);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-failed-generation", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);

        RESPONSE_STATUS.set(200);
        saveAuthorizedClient(registration(REVOCATION_URI), nextRefreshToken);
        assertThat(invalidate("session-new-generation", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);
        assertThat(storedClient()).isNull();

        synchronized (REQUESTS) {
            assertThat(REQUESTS).hasSize(3);
            assertThat(REQUESTS.get(1).form()).containsEntry("token", REFRESH_TOKEN);
            assertThat(REQUESTS.get(2).form()).containsEntry("token", nextRefreshToken);
        }
    }

    /**
     * A provider that never answers must surface as a failure, not as a blocked thread.
     *
     * <p>Without a read timeout the calling thread would block forever, the single-flight guard
     * would never be released, and every retry inside the TTL would answer "already claimed" —
     * so the caller would never learn the revocation failed. That is the silent failure the
     * retry machinery exists to prevent.
     */
    @Test
    void aHungProviderSurfacesAsRemoteFailureAndReleasesTheGuard() {
        String hangUri = "http://127.0.0.1:" + PROVIDER.getAddress().getPort()
                + "/protocol/openid-connect/hang";
        ClientRegistration hanging = registration(hangUri);

        authorizedClientService = new OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(hanging));
        revocationService = new OidcSessionRevocationService(
                new OidcRevocationClient(impatientRestClient()), authorizedClientService, clock);
        listener = new SessionInvalidationListener(revocationService);
        saveAuthorizedClient(hanging, REFRESH_TOKEN);

        long startedAt = System.nanoTime();
        assertThat(invalidate("session-l", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed)
                .as("must fail fast instead of waiting on the provider")
                .isLessThan(HANG_DURATION);

        // The guard was released, so the failure stays retriable rather than latching.
        assertThat(invalidate("session-l", SessionTerminationReason.SECURITY))
                .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
    }

    private static RestClient impatientRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(IMPATIENT_READ_TIMEOUT).build());
        requestFactory.setReadTimeout(IMPATIENT_READ_TIMEOUT);

        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Test
    void idleTimeoutAndDeviceLogoutDoNotRevoke() {
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-e", SessionTerminationReason.IDLE_TIMEOUT))
                .isEqualTo(OidcSessionRevocationService.Outcome.NOT_APPLICABLE);
        assertThat(invalidate("session-f", SessionTerminationReason.CURRENT_DEVICE_LOGOUT))
                .isEqualTo(OidcSessionRevocationService.Outcome.NOT_APPLICABLE);

        assertThat(REQUESTS).isEmpty();
        assertThat(storedClient())
                .isNotNull();
    }

    @Test
    void anAuthorizedClientWithoutRefreshTokenIsRemovedWithoutContactingTheProvider() {
        saveAuthorizedClient(registration(REVOCATION_URI), null);

        assertThat(invalidate("session-g", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.NO_REFRESH_TOKEN);
        assertThat(REQUESTS).isEmpty();
        assertThat(storedClient())
                .isNull();
    }

    @Test
    void repeatedInvalidationConvergesAndReplayStaysBoundedByTheTtl() {
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-h", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);
        assertThat(invalidate("session-h", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.ALREADY_CLAIMED);
        assertThat(REQUESTS).hasSize(1);

        clock.advance(OidcSessionRevocationService.PENDING_TTL.plusSeconds(1));
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        assertThat(invalidate("session-h", SessionTerminationReason.ADMINISTRATIVE))
                .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);
        assertThat(REQUESTS).hasSize(2);
    }

    @Test
    void revocationServiceAtomicallyTakesTheClientSavedInsideTheClaimWindow()
            throws Exception {
        ClientRegistration registration = registration(REVOCATION_URI);
        InterleavingAuthorizedClients clients = new InterleavingAuthorizedClients();
        authorizedClientService =
                new OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService(
                        new InMemoryClientRegistrationRepository(registration), clients);
        revocationService = new OidcSessionRevocationService(
                new OidcRevocationClient(RestClient.builder().build()),
                authorizedClientService,
                clock);
        listener = new SessionInvalidationListener(revocationService);
        saveAuthorizedClient(registration, REFRESH_TOKEN);

        clients.arm();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<OidcSessionRevocationService.Outcome> revocation = executor.submit(() ->
                    invalidate("session-atomic-claim", SessionTerminationReason.SECURITY));
            assertThat(clients.awaitClaimWindow()).isTrue();

            clock.advance(Duration.ofSeconds(1));
            String secondGenerationToken = "second-generation-token-must-never-leak";
            saveAuthorizedClient(registration, secondGenerationToken);
            clients.releaseClaim();

            assertThat(revocation.get(10, SECONDS))
                    .isEqualTo(OidcSessionRevocationService.Outcome.REVOKED);
            assertThat(secondGenerationToken.equals(singleRequest().form().get("token")))
                    .as("the production service must revoke the generation taken atomically")
                    .isTrue();
            OAuth2AuthorizedClient remaining = authorizedClientService.loadAuthorizedClient(
                    REGISTRATION_ID, PRINCIPAL);
            assertThat(remaining).isNull();
        } finally {
            clients.releaseClaim();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    @Test
    void aSecondGenerationCapturedWhileTheFirstIsInFlightRemainsPendingAndRunsSerially()
            throws Exception {
        String nextRefreshToken = "second-refresh-token-must-never-leak";
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
        AtomicInteger concurrentCalls = new AtomicInteger();
        AtomicInteger maximumConcurrentCalls = new AtomicInteger();
        List<String> attemptedTokens = Collections.synchronizedList(new ArrayList<>());
        OidcRevocationClient blockingClient = org.mockito.Mockito.mock(OidcRevocationClient.class);
        org.mockito.Mockito.doAnswer(invocation -> {
                    String token = invocation.getArgument(1, String.class);
                    int active = concurrentCalls.incrementAndGet();
                    maximumConcurrentCalls.accumulateAndGet(active, Math::max);
                    attemptedTokens.add(token);
                    try {
                        if (REFRESH_TOKEN.equals(token)) {
                            firstAttemptStarted.countDown();
                            assertThat(releaseFirstAttempt.await(10, SECONDS)).isTrue();
                        }
                        return null;
                    } finally {
                        concurrentCalls.decrementAndGet();
                    }
                })
                .when(blockingClient)
                .revokeRefreshToken(
                        org.mockito.ArgumentMatchers.any(ClientRegistration.class),
                        org.mockito.ArgumentMatchers.anyString());

        revocationService = new OidcSessionRevocationService(
                blockingClient, authorizedClientService, clock);
        listener = new SessionInvalidationListener(revocationService);
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<OidcSessionRevocationService.Outcome> first = executor.submit(() ->
                    invalidate("session-in-flight-a", SessionTerminationReason.SECURITY));
            assertThat(firstAttemptStarted.await(10, SECONDS)).isTrue();

            saveAuthorizedClient(registration(REVOCATION_URI), nextRefreshToken);
            assertThat(invalidate("session-in-flight-b", SessionTerminationReason.SECURITY))
                    .isEqualTo(OidcSessionRevocationService.Outcome.ALREADY_CLAIMED);
            assertThat(storedClient())
                    .as("the second generation is captured before reporting single-flight")
                    .isNull();

            releaseFirstAttempt.countDown();
            assertThat(first.get(10, SECONDS))
                    .as("the newly captured generation keeps the aggregate non-terminal")
                    .isEqualTo(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
            assertThat(revocationService.retryPendingGrant(
                            REGISTRATION_ID, PRINCIPAL, SessionTerminationReason.SECURITY))
                    .contains(OidcSessionRevocationService.Outcome.REVOKED);
        } finally {
            releaseFirstAttempt.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }

        assertThat(attemptedTokens).containsExactly(REFRESH_TOKEN, nextRefreshToken);
        assertThat(maximumConcurrentCalls.get()).isEqualTo(1);
    }

    @Test
    void concurrentInvalidationOfDifferentSessionsOfTheSameGrantRevokesExactlyOnce()
            throws Exception {
        saveAuthorizedClient(registration(REVOCATION_URI), REFRESH_TOKEN);

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OidcSessionRevocationService.Outcome>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workers; worker++) {
                String sessionId = "session-i-" + worker;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    return invalidate(sessionId, SessionTerminationReason.SECURITY);
                }));
            }

            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();

            List<OidcSessionRevocationService.Outcome> outcomes = new ArrayList<>();
            for (Future<OidcSessionRevocationService.Outcome> future : futures) {
                outcomes.add(future.get(20, SECONDS));
            }

            assertThat(outcomes)
                    .filteredOn(OidcSessionRevocationService.Outcome.REVOKED::equals)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(OidcSessionRevocationService.Outcome.ALREADY_CLAIMED::equals)
                    .hasSize(workers - 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }

        assertThat(REQUESTS).hasSize(1);
    }

    private OidcSessionRevocationService.Outcome invalidate(
            String sessionId, SessionTerminationReason reason) {
        return listener.onSessionInvalidated(new SessionInvalidationListener.SessionInvalidation(
                sessionId, REGISTRATION_ID, PRINCIPAL, reason));
    }

    private OAuth2AuthorizedClient storedClient() {
        return authorizedClientService.loadAuthorizedClient(REGISTRATION_ID, PRINCIPAL);
    }

    private void saveAuthorizedClient(ClientRegistration registration, String refreshToken) {
        Instant now = clock.instant();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-value",
                now.minusSeconds(60),
                now.plusSeconds(300));
        OAuth2RefreshToken refresh =
                (refreshToken == null) ? null : new OAuth2RefreshToken(refreshToken, now);
        Authentication principal =
                new TestingAuthenticationToken(PRINCIPAL, "n/a", "OIDC_USER");

        authorizedClientService.saveAuthorizedClient(
                new OAuth2AuthorizedClient(registration, PRINCIPAL, accessToken, refresh),
                principal);
    }

    private static ClientRegistration registration(String revocationUri) {
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://idp.invalid/authorize")
                .tokenUri("https://idp.invalid/token")
                .jwkSetUri("https://idp.invalid/jwks")
                .issuerUri("https://idp.invalid")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("BETUFU Revocation Contract");

        if (revocationUri != null) {
            builder.providerConfigurationMetadata(
                    Map.of(OidcRevocationClient.REVOCATION_ENDPOINT_METADATA, revocationUri));
        }

        return builder.build();
    }

    private static RecordedRequest singleRequest() {
        synchronized (REQUESTS) {
            assertThat(REQUESTS).hasSize(1);
            return REQUESTS.getFirst();
        }
    }

    private record RecordedRequest(
            String path, String method, String contentType, String authorization, String body) {

        Map<String, String> form() {
            Map<String, String> parsed = new java.util.LinkedHashMap<>();
            for (String pair : body.split("&")) {
                int separator = pair.indexOf('=');
                if (separator < 0) continue;
                parsed.put(
                        URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
            return parsed;
        }
    }

    /**
     * Opens a deterministic window immediately after a non-atomic get, or immediately before the
     * single atomic remove. The production implementation never calls get from take; a mutation
     * back to load-plus-remove does and therefore returns the old generation while deleting the
     * new one.
     */
    private static final class InterleavingAuthorizedClients
            extends ConcurrentHashMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient> {

        private final CountDownLatch claimWindow = new CountDownLatch(1);
        private final CountDownLatch releaseClaim = new CountDownLatch(1);
        private volatile boolean armed;

        void arm() {
            armed = true;
        }

        boolean awaitClaimWindow() throws InterruptedException {
            return claimWindow.await(10, SECONDS);
        }

        void releaseClaim() {
            releaseClaim.countDown();
        }

        @Override
        public OAuth2AuthorizedClient get(Object key) {
            OAuth2AuthorizedClient observed = super.get(key);
            awaitInterleavingIfArmed();
            return observed;
        }

        @Override
        public OAuth2AuthorizedClient remove(Object key) {
            awaitInterleavingIfArmed();
            return super.remove(key);
        }

        private void awaitInterleavingIfArmed() {
            if (!armed) {
                return;
            }
            claimWindow.countDown();
            try {
                if (!releaseClaim.await(10, SECONDS)) {
                    throw new AssertionError("claim interleaving was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("claim interleaving was interrupted", interrupted);
            }
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
