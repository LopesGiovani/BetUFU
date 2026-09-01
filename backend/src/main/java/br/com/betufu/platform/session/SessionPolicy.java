package br.com.betufu.platform.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class SessionPolicy {

  public static final Duration IDLE_TIMEOUT = Duration.ofHours(8);

  private final Clock clock;

  public SessionPolicy(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public SessionState start(
      String sessionId, String authenticationAcr, List<String> authenticationAmr) {
    return new SessionState(sessionId, clock.instant(), authenticationAcr, authenticationAmr, null);
  }

  public SessionActivity activity(SessionState session, SessionActivity.Kind kind) {
    Objects.requireNonNull(session, "session must not be null");
    return new SessionActivity(
        session.sessionId(), kind, clock.instant(), session.lastValidActivityAt());
  }

  public SessionState apply(SessionState session, SessionActivity activity) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(activity, "activity must not be null");

    if (!session.sessionId().equals(activity.sessionId())) {
      throw new IllegalArgumentException("activity belongs to a different session");
    }
    if (session.terminationReason() != null) {
      return session;
    }

    if (!activity.renewsIdleTimeout()) {
      return session;
    }
    if (reachedIdleDeadline(activity.observedLastValidActivityAt(), activity.occurredAt())) {
      return session;
    }
    if (!activity.occurredAt().isAfter(session.lastValidActivityAt())) {
      return session;
    }

    return session.withLastValidActivityAt(activity.occurredAt());
  }

  public SessionState evaluate(SessionState session) {
    Objects.requireNonNull(session, "session must not be null");
    if (session.terminationReason() != null) {
      return session;
    }
    return expireAt(session, clock.instant());
  }

  public boolean isExpired(SessionState session) {
    Objects.requireNonNull(session, "session must not be null");
    if (session.terminationReason() != null) {
      return true;
    }
    return reachedIdleDeadline(session, clock.instant());
  }

  public SessionState terminate(SessionState session, SessionTerminationReason reason) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    if (session.terminationReason() != null) {
      return session;
    }
    return session.withTerminationReason(reason);
  }

  public SessionContractDto toContract(
      SessionState session, String displayName, boolean mfaAuthenticated) {
    Objects.requireNonNull(session, "session must not be null");
    SessionState evaluated = evaluate(session);
    if (evaluated.terminationReason() != null) {
      return SessionContractDto.unauthenticated();
    }
    return SessionContractDto.authenticated(
        displayName, evaluated.lastValidActivityAt(), mfaAuthenticated);
  }

  private SessionState expireAt(SessionState session, Instant instant) {
    if (reachedIdleDeadline(session, instant)) {
      return session.withTerminationReason(SessionTerminationReason.IDLE_TIMEOUT);
    }
    return session;
  }

  private boolean reachedIdleDeadline(SessionState session, Instant instant) {
    return reachedIdleDeadline(session.lastValidActivityAt(), instant);
  }

  private boolean reachedIdleDeadline(Instant lastValidActivityAt, Instant instant) {
    Instant deadline = lastValidActivityAt.plus(IDLE_TIMEOUT);
    return !instant.isBefore(deadline);
  }

  public record SessionState(
      String sessionId,
      Instant lastValidActivityAt,
      String authenticationAcr,
      List<String> authenticationAmr,
      SessionTerminationReason terminationReason) {

    public SessionState {
      sessionId = requireText(sessionId, "sessionId");
      lastValidActivityAt =
          Objects.requireNonNull(lastValidActivityAt, "lastValidActivityAt must not be null");
      if (authenticationAcr != null && authenticationAcr.isBlank()) {
        throw new IllegalArgumentException("authenticationAcr must not be blank");
      }
      authenticationAmr =
          List.copyOf(
              Objects.requireNonNull(authenticationAmr, "authenticationAmr must not be null"));
    }

    private SessionState withLastValidActivityAt(Instant instant) {
      return new SessionState(
          sessionId, instant, authenticationAcr, authenticationAmr, terminationReason);
    }

    private SessionState withTerminationReason(SessionTerminationReason reason) {
      return new SessionState(
          sessionId, lastValidActivityAt, authenticationAcr, authenticationAmr, reason);
    }

    private static String requireText(String value, String fieldName) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " must not be blank");
      }
      return value;
    }
  }
}
