package br.com.betufu.platform.session;

import java.time.Instant;
import java.util.Objects;

public record SessionActivity(
    String sessionId, Kind kind, Instant occurredAt, Instant observedLastValidActivityAt) {

  public SessionActivity {
    sessionId = requireText(sessionId, "sessionId");
    kind = Objects.requireNonNull(kind, "kind must not be null");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    observedLastValidActivityAt =
        Objects.requireNonNull(
            observedLastValidActivityAt, "observedLastValidActivityAt must not be null");
  }

  public boolean renewsIdleTimeout() {
    return kind.renewsIdleTimeout();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public enum Kind {
    AUTHENTICATED_USER_API_V1_REQUEST(true),
    PROTECTED_NAVIGATION(true),
    ACTUATOR(false),
    STATIC_ASSET(false),
    SESSION_QUERY(false),
    POLLING(false),
    PREFLIGHT(false),
    HEARTBEAT(false),
    STOMP_HEARTBEAT(false),
    XSRF_MATERIALIZATION(false);

    private final boolean renewsIdleTimeout;

    Kind(boolean renewsIdleTimeout) {
      this.renewsIdleTimeout = renewsIdleTimeout;
    }

    public boolean renewsIdleTimeout() {
      return renewsIdleTimeout;
    }
  }
}
