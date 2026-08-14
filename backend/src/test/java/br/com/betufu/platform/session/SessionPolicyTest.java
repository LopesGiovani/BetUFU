package br.com.betufu.platform.session;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SessionPolicyTest {

    private static final Instant START = Instant.parse("2026-08-14T12:00:00.123456789Z");
    private static final String SESSION_ID = "opaque-session/device-a";

    @Test
    void representsEightHoursOfIdleTimeWithoutAnAbsoluteTimeout() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(
                SESSION_ID,
                "urn:betufu:acr:mfa",
                List.of("pwd", "otp"));

        SessionContractDto contract = policy.toContract(session, "Conta técnica", true);

        assertThat(contract.authenticated()).isTrue();
        assertThat(contract.displayName()).isEqualTo("Conta técnica");
        assertThat(contract.idleTimeout()).isEqualTo(Duration.ofHours(8));
        assertThat(contract.absoluteTimeout()).isNull();
        assertThat(contract.lastValidActivityAt()).isEqualTo(START);
        assertThat(contract.mfaAuthenticated()).isTrue();
        assertThat(policy.toContract(session, null, true).displayName()).isNull();
        assertThat(Arrays.stream(SessionContractDto.class.getRecordComponents())
                        .map(component -> component.getName()))
                .doesNotContain("sessionId", "authenticationAcr", "authenticationAmr");
    }

    @Test
    void remainsActiveOneMillisecondBeforeTheBoundaryAndExpiresAtExactlyEightHours() {
        SessionPolicy.SessionState session = policyAt(START).start(SESSION_ID, null, List.of());

        assertThat(policyAt(START.plus(SessionPolicy.IDLE_TIMEOUT).minusMillis(1))
                        .isExpired(session))
                .isFalse();
        assertThat(policyAt(START.plus(SessionPolicy.IDLE_TIMEOUT)).isExpired(session))
                .isTrue();
        assertThat(policyAt(START.plus(SessionPolicy.IDLE_TIMEOUT))
                        .evaluate(session)
                        .terminationReason())
                .isEqualTo(SessionTerminationReason.IDLE_TIMEOUT);
    }

    @Test
    void preservesNanosecondPrecisionWhenRenewingTheIdleDeadline() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());
        Instant activityAt = START.plusSeconds(17).plusNanos(321);

        SessionPolicy.SessionState renewed = policy.apply(
                session,
                new SessionActivity(
                        SESSION_ID,
                        SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                        activityAt,
                        START));

        assertThat(renewed.lastValidActivityAt()).isEqualTo(activityAt);
        assertThat(policyAt(activityAt.plus(SessionPolicy.IDLE_TIMEOUT).minusNanos(1))
                        .isExpired(renewed))
                .isFalse();
        assertThat(policyAt(activityAt.plus(SessionPolicy.IDLE_TIMEOUT)).isExpired(renewed))
                .isTrue();
    }

    @Test
    void renewsOnlyForAuthenticatedApiActivityOrProtectedNavigation() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());
        Instant apiAt = START.plusSeconds(1);
        Instant navigationAt = START.plusSeconds(2);

        SessionPolicy.SessionState afterApi = policy.apply(
                session,
                new SessionActivity(
                        SESSION_ID,
                        SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                        apiAt,
                        START));
        SessionPolicy.SessionState afterNavigation = policy.apply(
                afterApi,
                new SessionActivity(
                        SESSION_ID,
                        SessionActivity.Kind.PROTECTED_NAVIGATION,
                        navigationAt,
                        apiAt));

        assertThat(afterApi.lastValidActivityAt()).isEqualTo(apiAt);
        assertThat(afterNavigation.lastValidActivityAt()).isEqualTo(navigationAt);
    }

    @Test
    void technicalTrafficAndXsrfMaterializationNeverRenewIdleTime() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());

        for (SessionActivity.Kind kind : SessionActivity.Kind.values()) {
            if (kind.renewsIdleTimeout()) {
                continue;
            }

            SessionPolicy.SessionState result = policy.apply(
                    session,
                    new SessionActivity(SESSION_ID, kind, START.plusSeconds(30), START));

            assertThat(result)
                    .as("kind %s must not renew idle time", kind)
                    .isSameAs(session);
        }
    }

    @Test
    void applyingTheSameActivityTwiceIsIdempotent() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());
        SessionActivity activity = new SessionActivity(
                SESSION_ID,
                SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                START.plusSeconds(10),
                START);

        SessionPolicy.SessionState first = policy.apply(session, activity);
        SessionPolicy.SessionState second = policy.apply(first, activity);

        assertThat(second).isSameAs(first);
        assertThat(second.sessionId()).isEqualTo(SESSION_ID);
        assertThat(second.lastValidActivityAt()).isEqualTo(activity.occurredAt());
    }

    @Test
    void clockRegressionAndOutOfOrderEventsCannotMoveActivityBackwards() {
        SessionPolicy policy = policyAt(START.minusSeconds(30));
        SessionPolicy.SessionState session = new SessionPolicy.SessionState(
                SESSION_ID,
                START,
                null,
                List.of(),
                null);

        SessionPolicy.SessionState result = policy.apply(
                session,
                policy.activity(
                        session,
                        SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST));

        assertThat(result).isSameAs(session);
        assertThat(result.lastValidActivityAt()).isEqualTo(START);
    }

    @Test
    void activityAtTheExactDeadlineCannotReviveTheSession() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());

        SessionPolicy.SessionState result = policy.apply(
                session,
                new SessionActivity(
                        SESSION_ID,
                        SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                        START.plus(SessionPolicy.IDLE_TIMEOUT),
                        START));

        assertThat(result.lastValidActivityAt()).isEqualTo(START);
        assertThat(result.terminationReason()).isNull();
        assertThat(policyAt(START.plus(SessionPolicy.IDLE_TIMEOUT))
                        .evaluate(result)
                        .terminationReason())
                .isEqualTo(SessionTerminationReason.IDLE_TIMEOUT);
    }

    @Test
    void concurrentActivitiesConvergeOnTheGreatestValidInstant() throws Exception {
        int workers = 10;
        SessionPolicy policy = policyAt(START);
        AtomicReference<SessionPolicy.SessionState> state = new AtomicReference<>(
                policy.start(SESSION_ID, null, List.of()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int worker = 1; worker <= workers; worker++) {
                Instant occurredAt = worker == workers
                        ? START.plus(SessionPolicy.IDLE_TIMEOUT)
                        : worker == workers - 1
                                ? START.plus(SessionPolicy.IDLE_TIMEOUT).minusMillis(1)
                                : START.plusSeconds(worker);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    SessionActivity activity = new SessionActivity(
                            SESSION_ID,
                            SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                            occurredAt,
                            START);
                    state.updateAndGet(current -> policy.apply(current, activity));
                    return null;
                }));
            }

            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, SECONDS);
            }

            SessionPolicy.SessionState converged = state.get();
            assertThat(converged.lastValidActivityAt())
                    .isEqualTo(START.plus(SessionPolicy.IDLE_TIMEOUT).minusMillis(1));
            assertThat(converged.terminationReason()).isNull();
            assertThat(policyAt(converged.lastValidActivityAt().plus(SessionPolicy.IDLE_TIMEOUT))
                            .isExpired(converged))
                    .isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    @Test
    void activityCannotCrossDeviceSessionBoundaries() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState deviceA = policy.start("opaque-device-a", null, List.of());
        SessionPolicy.SessionState deviceB = policy.start("opaque-device-b", null, List.of());
        SessionActivity activityA = new SessionActivity(
                deviceA.sessionId(),
                SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
                START.plusSeconds(1),
                START);

        assertThatThrownBy(() -> policy.apply(deviceB, activityA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activity belongs to a different session");
        assertThat(deviceB.lastValidActivityAt()).isEqualTo(START);
    }

    @Test
    void authenticationSnapshotIsImmutableAndDoesNotTriggerStepUp() {
        List<String> authenticationAmr = new ArrayList<>(List.of("pwd", "otp"));
        SessionPolicy policy = policyAt(START);

        SessionPolicy.SessionState session = policy.start(
                SESSION_ID,
                "urn:betufu:acr:mfa",
                authenticationAmr);
        authenticationAmr.add("webauthn");
        SessionPolicy.SessionState renewed = policy.apply(
                session,
                new SessionActivity(
                        SESSION_ID,
                        SessionActivity.Kind.PROTECTED_NAVIGATION,
                        START.plusSeconds(1),
                        START));

        assertThat(renewed.authenticationAcr()).isEqualTo("urn:betufu:acr:mfa");
        assertThat(renewed.authenticationAmr()).containsExactly("pwd", "otp");
        assertThatThrownBy(() -> renewed.authenticationAmr().add("sms"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void terminationReasonIsExplicitAndCannotBeOverwritten() {
        SessionPolicy policy = policyAt(START);
        SessionPolicy.SessionState session = policy.start(SESSION_ID, null, List.of());

        SessionPolicy.SessionState terminated = policy.terminate(
                session, SessionTerminationReason.ADMINISTRATIVE);
        SessionPolicy.SessionState repeated = policy.terminate(
                terminated, SessionTerminationReason.SECURITY);

        assertThat(terminated.terminationReason())
                .isEqualTo(SessionTerminationReason.ADMINISTRATIVE);
        assertThat(repeated).isSameAs(terminated);
        assertThat(policy.isExpired(terminated)).isTrue();
    }

    private SessionPolicy policyAt(Instant instant) {
        return new SessionPolicy(Clock.fixed(instant, ZoneOffset.UTC));
    }
}
