package br.com.betufu.platform.session;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the RP-initiated logout URI that ends the session at the identity provider.
 *
 * <p>Coordination is front-channel by necessity: only the browser carries the provider's own
 * session cookie, so a server-side call could never end the provider session. This class therefore
 * produces a URI for the SPA to navigate to; it never contacts the provider itself.
 *
 * <p><strong>No {@code id_token_hint}.</strong> Plan 01-22's Task 2 action mentions the hint, but
 * its own acceptance criterion forbids any token appearing in a returned URL, body or log, and the
 * BFF invariant of this phase keeps authentication material server-side. The ID token is
 * authentication material, and a URL handed to the browser leaks through history, {@code Referer}
 * and proxy logs. RP-Initiated Logout 1.0 allows {@code client_id} instead, which is what this
 * builds. The cost is that the provider may show a logout confirmation screen, since without the
 * hint it cannot identify the session non-interactively.
 *
 * <p><strong>Fail closed.</strong> {@code end_session_endpoint} is read only from the discovery
 * metadata, never guessed from the issuer, mirroring how {@code revocation_endpoint} is resolved in
 * {@link OidcSessionRevocationService}. A missing or unusable value yields an empty result rather
 * than a fabricated URL.
 *
 * <p>{@code post_logout_redirect_uri} is derived from the current request, so it is same-origin by
 * construction rather than by an allowlist this plan does not own. The provider's registered
 * post-logout redirect list stays the authoritative check. Plan 01-08 is where the explicit
 * {@code betufu.web.*} origin contract replaces this derivation.
 */
public final class OidcLogoutSuccessHandler {

    private static final String END_SESSION_ENDPOINT = "end_session_endpoint";
    private static final String CLIENT_ID_PARAMETER = "client_id";
    private static final String POST_LOGOUT_REDIRECT_URI_PARAMETER = "post_logout_redirect_uri";

    /**
     * @return the provider logout URI, or {@link Optional#empty()} when the registration is absent
     *     or its discovery metadata carries no usable {@code end_session_endpoint}
     */
    public Optional<URI> endSessionUri(
            ClientRegistration registration, HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (registration == null) {
            return Optional.empty();
        }

        Object metadataValue = registration.getProviderDetails()
                .getConfigurationMetadata()
                .get(END_SESSION_ENDPOINT);
        if (!(metadataValue instanceof String endSessionEndpoint)
                || endSessionEndpoint.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(UriComponentsBuilder.fromUriString(endSessionEndpoint)
                .queryParam(CLIENT_ID_PARAMETER, registration.getClientId())
                .queryParam(POST_LOGOUT_REDIRECT_URI_PARAMETER, postLogoutRedirectUri(request))
                .encode()
                .build()
                .toUri());
    }

    private static String postLogoutRedirectUri(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request)
                .path("/")
                .build()
                .toUriString();
    }
}
