package br.com.betufu.platform.security;

import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Server-side OIDC token revocation, driven exclusively by provider discovery.
 *
 * <p>The endpoint is read from the {@code ClientRegistration} discovery metadata
 * ({@code revocation_endpoint}). A fixed or derived URL is never used: if the provider does not
 * publish the capability, revocation fails closed rather than guessing an address.
 *
 * <p>This class only ever revokes a <em>refresh</em> token. There is deliberately no overload
 * accepting an access token, so an access-token fallback cannot be introduced by mistake.
 *
 * <p><strong>Redaction.</strong> Neither the token nor the remote response body ever reaches an
 * exception message, a log, a metric or a Problem Details payload. Failures are reported with the
 * HTTP status code or the transport failure type only, and remote exceptions are never chained as
 * a cause, because their message can embed the response body.
 */
public final class OidcRevocationClient {

    /** Discovery key that names the endpoint; public so tests can assert the exact contract. */
    public static final String REVOCATION_ENDPOINT_METADATA = "revocation_endpoint";

    static final String TOKEN_PARAMETER = "token";
    static final String TOKEN_TYPE_HINT_PARAMETER = "token_type_hint";
    static final String REFRESH_TOKEN_HINT = "refresh_token";

    private final RestClient restClient;

    public OidcRevocationClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    /**
     * Revokes a refresh token at the provider's discovered revocation endpoint.
     *
     * @throws OidcRevocationException when discovery does not publish the endpoint, when the
     *     provider answers with a non-successful status, or when the request cannot be delivered
     */
    public void revokeRefreshToken(ClientRegistration registration, String refreshToken) {
        Objects.requireNonNull(registration, "registration must not be null");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new OidcRevocationException("Refresh token must not be blank");
        }

        String endpoint = resolveRevocationEndpoint(registration);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(TOKEN_PARAMETER, refreshToken);
        form.add(TOKEN_TYPE_HINT_PARAMETER, REFRESH_TOKEN_HINT);

        ResponseEntity<Void> response;
        try {
            response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> headers.setBasicAuth(
                            registration.getClientId(), registration.getClientSecret()))
                    .body(form)
                    .retrieve()
                    // Suppress the default error handler: its exception message carries the
                    // remote response body, which must never leave this method.
                    .onStatus(status -> true, (request, remoteResponse) -> { })
                    .toBodilessEntity();
        } catch (RestClientException failure) {
            throw new OidcRevocationException(
                    "Revocation request could not be delivered: "
                            + failure.getClass().getSimpleName());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new OidcRevocationException(
                    "Revocation endpoint answered with status "
                            + response.getStatusCode().value());
        }
    }

    private static String resolveRevocationEndpoint(ClientRegistration registration) {
        Object endpoint = registration.getProviderDetails()
                .getConfigurationMetadata()
                .get(REVOCATION_ENDPOINT_METADATA);

        if (!(endpoint instanceof String uri) || uri.isBlank()) {
            throw new OidcRevocationException(
                    "Provider discovery does not publish a revocation_endpoint");
        }

        return uri;
    }

    /** Carries only non-sensitive failure context: never a token, never a remote body. */
    public static final class OidcRevocationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        OidcRevocationException(String message) {
            super(message);
        }
    }
}
