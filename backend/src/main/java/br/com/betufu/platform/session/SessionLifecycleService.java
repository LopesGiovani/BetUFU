package br.com.betufu.platform.session;

import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE;
import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.AUTHENTICATION_ACR_ATTRIBUTE;
import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE;
import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.AUTHENTICATION_AMR_PREFIX;
import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE;
import static br.com.betufu.platform.session.SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

public final class SessionLifecycleService implements AuthenticationSuccessHandler {

  private final SessionPolicy sessionPolicy;
  private final Clock clock;
  private final AuthenticationSuccessHandler delegate;

  public SessionLifecycleService(SessionPolicy sessionPolicy, Clock clock) {
    this(sessionPolicy, clock, new SavedRequestAwareAuthenticationSuccessHandler());
  }

  SessionLifecycleService(
      SessionPolicy sessionPolicy, Clock clock, AuthenticationSuccessHandler delegate) {
    this.sessionPolicy = Objects.requireNonNull(sessionPolicy, "sessionPolicy must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(authentication, "authentication must not be null");

    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new IllegalStateException(
          "OIDC authentication succeeded without an existing HTTP session");
    }

    try {
      AuthenticationSnapshot snapshot = authenticationSnapshot(authentication);
      Instant authenticatedAt = clock.instant();
      replaceState(
          session,
          new SessionPolicy.SessionState(
              session.getId(), authenticatedAt, snapshot.acr(), snapshot.amr(), null));
      session.setAttribute(APPROVED_ACTIVITY_ATTRIBUTE, authenticatedAt);
      delegate.onAuthenticationSuccess(request, response, authentication);
    } catch (IOException | ServletException | RuntimeException | Error failure) {
      invalidateQuietly(session);
      throw failure;
    }
  }

  public void recordSuccessfulActivity(HttpServletRequest request, Authentication authentication) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(authentication, "authentication must not be null");

    HttpSession session = request.getSession(false);
    if (session == null) {
      return;
    }

    try {
      SessionPolicy.SessionState current = readState(session);
      if (current == null) {
        AuthenticationSnapshot snapshot = authenticationSnapshot(authentication);
        Instant initializedAt = clock.instant();
        SessionPolicy.SessionState initialized =
            new SessionPolicy.SessionState(
                session.getId(), initializedAt, snapshot.acr(), snapshot.amr(), null);
        replaceState(session, initialized);
        session.setAttribute(APPROVED_ACTIVITY_ATTRIBUTE, initializedAt);
        return;
      }

      Instant occurredAt = clock.instant();
      if (!occurredAt.isBefore(current.lastValidActivityAt().plus(SessionPolicy.IDLE_TIMEOUT))) {
        throw new IllegalStateException(
            "Cannot renew an HTTP session that reached its idle deadline");
      }

      SessionActivity activity =
          new SessionActivity(
              current.sessionId(),
              SessionActivity.Kind.AUTHENTICATED_USER_API_V1_REQUEST,
              occurredAt,
              current.lastValidActivityAt());
      SessionPolicy.SessionState updated = sessionPolicy.apply(current, activity);
      writeLastValidActivity(session, updated.lastValidActivityAt());
      session.setAttribute(APPROVED_ACTIVITY_ATTRIBUTE, updated.lastValidActivityAt());
    } catch (RuntimeException | Error failure) {
      invalidateQuietly(session);
      throw failure;
    }
  }

  /**
   * Reads the lifecycle state of the current HTTP session without touching it.
   *
   * <p>Plan 01-22 publishes a session contract endpoint, which needs a read path. Exposing this
   * reader keeps a single parser for the Redis representation: decoding the attributes again in the
   * controller would create two readers that can drift apart, and the integrity checks in {@link
   * #readState} — nano range and AMR completeness — would only exist in one of them.
   *
   * <p>Deliberately does not create a session, does not renew the idle deadline and does not
   * invalidate on malformed state. A corrupt snapshot propagates as {@link IllegalStateException}
   * so the caller fails closed instead of publishing a half-read contract. Unlike the mutating
   * paths above, a pure read must not be able to terminate a session as a side effect.
   *
   * @return the current state, or {@code null} when there is no session or no lifecycle state
   */
  public SessionPolicy.SessionState currentState(HttpServletRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    return readState(session);
  }

  private SessionPolicy.SessionState readState(HttpSession session) {
    List<String> attributeNames = Collections.list(session.getAttributeNames());
    boolean hasTemporalState =
        attributeNames.contains(LAST_VALID_EPOCH_SECOND_ATTRIBUTE)
            || attributeNames.contains(LAST_VALID_NANO_ATTRIBUTE);
    boolean hasAuthenticationState =
        attributeNames.contains(AUTHENTICATION_ACR_ATTRIBUTE)
            || attributeNames.contains(AUTHENTICATION_AMR_COUNT_ATTRIBUTE)
            || attributeNames.stream().anyMatch(SessionLifecycleService::isAmrItemAttribute);
    boolean hasApprovedActivity = attributeNames.contains(APPROVED_ACTIVITY_ATTRIBUTE);

    if (!hasTemporalState && !hasAuthenticationState && !hasApprovedActivity) {
      return null;
    }

    Long epochSecond = requireAttribute(session, LAST_VALID_EPOCH_SECOND_ATTRIBUTE, Long.class);
    Integer nano = requireAttribute(session, LAST_VALID_NANO_ATTRIBUTE, Integer.class);
    Integer amrCount = requireAttribute(session, AUTHENTICATION_AMR_COUNT_ATTRIBUTE, Integer.class);
    if (nano < 0 || nano > 999_999_999) {
      throw new IllegalStateException("Session last-valid nano is outside its range");
    }

    long storedAmrItems =
        attributeNames.stream().filter(SessionLifecycleService::isAmrItemAttribute).count();
    if (amrCount < 0 || amrCount.longValue() != storedAmrItems) {
      throw new IllegalStateException("Session AMR snapshot is incomplete");
    }

    List<String> amr = new ArrayList<>(amrCount);
    for (int index = 0; index < amrCount; index++) {
      amr.add(requireTextAttribute(session, AUTHENTICATION_AMR_PREFIX + index));
    }

    Object acrValue = session.getAttribute(AUTHENTICATION_ACR_ATTRIBUTE);
    String acr = null;
    if (acrValue != null) {
      if (!(acrValue instanceof String acrText) || acrText.isBlank()) {
        throw new IllegalStateException("Session ACR snapshot is invalid");
      }
      acr = acrText;
    }

    return new SessionPolicy.SessionState(
        session.getId(), Instant.ofEpochSecond(epochSecond, nano.longValue()), acr, amr, null);
  }

  private void replaceState(HttpSession session, SessionPolicy.SessionState state) {
    clearState(session);
    writeLastValidActivity(session, state.lastValidActivityAt());
    if (state.authenticationAcr() != null) {
      session.setAttribute(AUTHENTICATION_ACR_ATTRIBUTE, state.authenticationAcr());
    }
    session.setAttribute(AUTHENTICATION_AMR_COUNT_ATTRIBUTE, state.authenticationAmr().size());
    for (int index = 0; index < state.authenticationAmr().size(); index++) {
      session.setAttribute(AUTHENTICATION_AMR_PREFIX + index, state.authenticationAmr().get(index));
    }
  }

  private void writeLastValidActivity(HttpSession session, Instant instant) {
    session.setAttribute(LAST_VALID_EPOCH_SECOND_ATTRIBUTE, instant.getEpochSecond());
    session.setAttribute(LAST_VALID_NANO_ATTRIBUTE, instant.getNano());
  }

  private void clearState(HttpSession session) {
    session.removeAttribute(APPROVED_ACTIVITY_ATTRIBUTE);
    List<String> attributeNames = Collections.list(session.getAttributeNames());
    for (String attributeName : attributeNames) {
      if (attributeName.equals(LAST_VALID_EPOCH_SECOND_ATTRIBUTE)
          || attributeName.equals(LAST_VALID_NANO_ATTRIBUTE)
          || attributeName.equals(AUTHENTICATION_ACR_ATTRIBUTE)
          || attributeName.equals(AUTHENTICATION_AMR_COUNT_ATTRIBUTE)
          || attributeName.equals(APPROVED_ACTIVITY_ATTRIBUTE)
          || attributeName.startsWith(AUTHENTICATION_AMR_PREFIX)) {
        session.removeAttribute(attributeName);
      }
    }
  }

  private AuthenticationSnapshot authenticationSnapshot(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new IllegalStateException("Session authentication principal must be an OIDC user");
    }

    Object acrClaim = oidcUser.getClaim("acr");
    String acr = null;
    if (acrClaim != null) {
      if (!(acrClaim instanceof String acrText) || acrText.isBlank()) {
        throw new IllegalStateException("OIDC acr claim must be a non-blank string");
      }
      acr = acrText;
    }

    Object amrClaim = oidcUser.getClaim("amr");
    if (amrClaim == null) {
      return new AuthenticationSnapshot(acr, List.of());
    }
    if (!(amrClaim instanceof Iterable<?> values)) {
      throw new IllegalStateException("OIDC amr claim must be an array of strings");
    }

    List<String> amr = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof String method) || method.isBlank()) {
        throw new IllegalStateException("OIDC amr claim must contain only non-blank strings");
      }
      amr.add(method);
    }
    return new AuthenticationSnapshot(acr, amr);
  }

  private static <T> T requireAttribute(HttpSession session, String name, Class<T> type) {
    Object value = session.getAttribute(name);
    if (!type.isInstance(value)) {
      throw new IllegalStateException("Session state is incomplete or invalid");
    }
    return type.cast(value);
  }

  private static String requireTextAttribute(HttpSession session, String name) {
    Object value = session.getAttribute(name);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalStateException("Session AMR snapshot is incomplete");
    }
    return text;
  }

  private static boolean isAmrItemAttribute(String name) {
    return name.startsWith(AUTHENTICATION_AMR_PREFIX)
        && !name.equals(AUTHENTICATION_AMR_COUNT_ATTRIBUTE);
  }

  private static void invalidateQuietly(HttpSession session) {
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // The session was already invalidated by another fail-closed path.
    }
  }

  private record AuthenticationSnapshot(String acr, List<String> amr) {

    private AuthenticationSnapshot {
      amr = List.copyOf(amr);
    }
  }
}
