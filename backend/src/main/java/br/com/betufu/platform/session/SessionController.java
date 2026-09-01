package br.com.betufu.platform.session;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the session state the browser is allowed to know.
 *
 * <p>The BFF keeps every piece of authentication material server-side, so this endpoint exists to
 * let the SPA answer "am I signed in, and until when?" without ever receiving a session id, a
 * cookie value, a token or the raw ID token claims.
 *
 * <p>The query is deliberately non-renewing. {@code ValidActivityFilter} allowlists exactly one
 * renewing request — the authenticated tracer PUT — so reaching this handler cannot advance the
 * idle deadline. {@code SessionControllerIT} pins that by reading the persisted {@code
 * lastAccessedTime} and the Redis TTL around the call, because the property currently holds by
 * absence of code and would otherwise regress silently.
 */
@RestController
@Profile("runtime | integrated")
public class SessionController {

  private static final String SESSION_PATH = "/api/v1/session";

  private final SessionLifecycleService sessionLifecycleService;
  private final SessionPolicy sessionPolicy;

  public SessionController(
      SessionLifecycleService sessionLifecycleService, SessionPolicy sessionPolicy) {
    this.sessionLifecycleService =
        Objects.requireNonNull(sessionLifecycleService, "sessionLifecycleService must not be null");
    this.sessionPolicy = Objects.requireNonNull(sessionPolicy, "sessionPolicy must not be null");
  }

  @GetMapping(path = SESSION_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SessionStateResponse> getSession(
      HttpServletRequest request,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    return ResponseEntity.ok(SessionStateResponse.from(currentContract(request, oidcUser)));
  }

  /**
   * Resolves the contract without ever creating or renewing a session.
   *
   * <p>Three distinct situations collapse into the unauthenticated projection: no principal, a
   * principal without lifecycle state, and a session whose idle deadline has passed. The third is
   * decided by {@link SessionPolicy#toContract}, not here, so the endpoint cannot disagree with the
   * policy that {@code ValidActivityFilter} and the Redis repository enforce.
   */
  private SessionContractDto currentContract(HttpServletRequest request, OidcUser oidcUser) {
    if (oidcUser == null) {
      return SessionContractDto.unauthenticated();
    }

    SessionPolicy.SessionState state = sessionLifecycleService.currentState(request);
    if (state == null) {
      return SessionContractDto.unauthenticated();
    }

    // The third argument is the policy DTO's mfaAuthenticated flag, which this endpoint does
    // not publish: no approved rule maps acr/amr onto it. See SessionStateResponse.
    return sessionPolicy.toContract(state, displayName(oidcUser), false);
  }

  /**
   * Reads {@code preferred_username} straight from the validated ID token.
   *
   * <p>Only a genuinely non-blank {@code String} is accepted. That claim is never type-converted by
   * {@code OidcIdTokenDecoderFactory}, so coercing another shape through {@code toString()} could
   * turn a malformed token into a plausible-looking display name. A missing or unusable claim
   * yields {@code null}, which {@link SessionContractDto} allows: an unusable display claim must
   * not turn a valid session into an error.
   */
  private static String displayName(OidcUser oidcUser) {
    Object value = oidcUser.getIdToken().getClaims().get(StandardClaimNames.PREFERRED_USERNAME);

    return (value instanceof String text && !text.isBlank()) ? text : null;
  }

  /**
   * The published HTTP contract, deliberately separate from {@link SessionContractDto}.
   *
   * <p>{@code SessionContractDto} is the policy projection introduced by Plan 01-06; this record is
   * the public API. Keeping them apart stops policy evolution from leaking into the API and vice
   * versa — annotating the policy DTO instead would create the opposite coupling.
   *
   * <p>{@code mfaAuthenticated} is absent on purpose. No approved rule maps {@code acr} or {@code
   * amr} onto that boolean: D-07 only states that MFA performed at login stays valid for the
   * session, and user-facing MFA is deferred to v2. Publishing the field would freeze an invented
   * rule into a public contract.
   *
   * <p>{@code absoluteTimeout} is always {@code null} and is still emitted, because D-06 requires
   * the absence of an absolute deadline to be represented explicitly rather than omitted.
   *
   * <p>{@code idleTimeout} serializes as ISO-8601 {@code "PT8H"}. That rests on {@code
   * DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS} being disabled by default in jackson-databind
   * 3.1.4 — a library default this project does not pin, and which the Boot auto-configuration does
   * not enforce here either, since its {@code disable(...)} call is guarded by {@code
   * spring.jackson.use-jackson2-defaults}, which is off. {@code SessionControllerIT} therefore
   * asserts the literal string in the response bytes.
   */
  public record SessionStateResponse(
      boolean authenticated,
      String displayName,
      Duration idleTimeout,
      Duration absoluteTimeout,
      Instant lastValidActivityAt) {

    static SessionStateResponse from(SessionContractDto contract) {
      Objects.requireNonNull(contract, "contract must not be null");

      return new SessionStateResponse(
          contract.authenticated(),
          contract.displayName(),
          contract.idleTimeout(),
          contract.absoluteTimeout(),
          contract.lastValidActivityAt());
    }
  }
}
