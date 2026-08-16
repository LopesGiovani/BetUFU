package br.com.betufu.platform.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ends the current device's session, and only that one.
 *
 * <p>Per D-08 sessions on different devices are independent, and per D-09 the voluntary "Sair"
 * ends the current device alone. This endpoint therefore never enumerates the sessions of an
 * {@code (iss, sub)} pair and offers no logout-all. Administrative and security revocation remain a
 * separate path through {@link SessionInvalidationListener}.
 *
 * <p><strong>Ordering is the security contract</strong>, mirroring
 * {@link OidcSessionRevocationService}: the local state is made terminal <em>before</em> the
 * provider coordination is even computed. A coordination failure can therefore never revive the
 * session — by the time anything can go wrong, the authorized client is gone and the HTTP session
 * is invalid.
 *
 * <p>The authorized client is removed but the grant is <em>not</em> revoked. That is deliberate and
 * matches {@code OidcSessionRevocationService}, which revokes only for {@code ADMINISTRATIVE} and
 * {@code SECURITY} reasons: voluntary logout ends a browser session, not the identity grant. What
 * ends the provider session is the front-channel navigation the response carries.
 *
 * <p>POST only, so a link or prefetched GET cannot end a session, and CSRF-protected by the
 * chain-wide SPA configuration.
 */
@RestController
@Profile("runtime | integrated")
public class CurrentDeviceLogoutController {

    private static final String LOGOUT_PATH = "/api/v1/session/logout";

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OidcLogoutSuccessHandler logoutSuccessHandler;

    public CurrentDeviceLogoutController(
            OAuth2AuthorizedClientService authorizedClientService,
            ClientRegistrationRepository clientRegistrationRepository,
            OidcLogoutSuccessHandler logoutSuccessHandler) {
        this.authorizedClientService = Objects.requireNonNull(
                authorizedClientService, "authorizedClientService must not be null");
        this.clientRegistrationRepository = Objects.requireNonNull(
                clientRegistrationRepository, "clientRegistrationRepository must not be null");
        this.logoutSuccessHandler = Objects.requireNonNull(
                logoutSuccessHandler, "logoutSuccessHandler must not be null");
    }

    @PostMapping(path = LOGOUT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentDeviceLogoutResponse> logoutCurrentSession(
            HttpServletRequest request, Authentication authentication) {
        String registrationId = registrationId(authentication);
        String principalName = (authentication == null) ? null : authentication.getName();

        ClientRegistration registration = resolveRegistration(registrationId);

        terminateLocally(request, registrationId, principalName);

        Optional<URI> endSessionUri = logoutSuccessHandler.endSessionUri(registration, request);
        if (endSessionUri.isEmpty()) {
            // The session is already gone; the provider session may not be. Surfacing this as an
            // error rather than a success keeps the failure visible instead of silent, and the
            // body carries no reason, endpoint or identifier.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(CurrentDeviceLogoutResponse.endSessionUnavailable());
        }

        return ResponseEntity.ok(
                CurrentDeviceLogoutResponse.loggedOut(endSessionUri.get().toString()));
    }

    /**
     * Makes the local state terminal.
     *
     * <p>Runs before any coordination and swallows nothing: the authorized client goes first so a
     * later failure cannot leave a reusable grant behind, then the HTTP session, then the thread's
     * security context.
     */
    private void terminateLocally(
            HttpServletRequest request, String registrationId, String principalName) {
        if (registrationId != null && principalName != null) {
            authorizedClientService.removeAuthorizedClient(registrationId, principalName);
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // Another fail-closed path already ended it; the post-condition still holds.
            }
        }

        SecurityContextHolder.clearContext();
    }

    private ClientRegistration resolveRegistration(String registrationId) {
        return (registrationId == null)
                ? null
                : clientRegistrationRepository.findByRegistrationId(registrationId);
    }

    private static String registrationId(Authentication authentication) {
        return (authentication instanceof OAuth2AuthenticationToken token)
                ? token.getAuthorizedClientRegistrationId()
                : null;
    }

    /**
     * The published HTTP contract, kept separate from any internal projection for the same reason
     * as {@code SessionController.SessionStateResponse}.
     *
     * <p>{@code endSessionUrl} is a provider URL carrying only {@code client_id} and a same-origin
     * {@code post_logout_redirect_uri} — no {@code id_token_hint}, no access or refresh token, no
     * session identifier. See {@link OidcLogoutSuccessHandler}.
     */
    public record CurrentDeviceLogoutResponse(String status, String endSessionUrl) {

        private static final String LOGGED_OUT = "LOGGED_OUT";
        private static final String END_SESSION_UNAVAILABLE = "END_SESSION_UNAVAILABLE";

        static CurrentDeviceLogoutResponse loggedOut(String endSessionUrl) {
            return new CurrentDeviceLogoutResponse(
                    LOGGED_OUT, Objects.requireNonNull(endSessionUrl));
        }

        static CurrentDeviceLogoutResponse endSessionUnavailable() {
            return new CurrentDeviceLogoutResponse(END_SESSION_UNAVAILABLE, null);
        }
    }
}
