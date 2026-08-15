package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import br.com.betufu.platform.security.OidcRevocationClient;

/**
 * Proves refresh-token revocation against a real Keycloak.
 *
 * <p>The refresh token is obtained through a genuine Authorization Code exchange — the realm's
 * direct access grant stays disabled, matching the phase COVERAGE decision. The flow is driven
 * over HTTP rather than a browser: fetch the login page, post the credential, capture the
 * redirect, exchange the code.
 *
 * <p>The assertion is deliberately scoped to what revocation actually guarantees: a subsequent
 * <em>refresh grant</em> fails with {@code invalid_grant}. No claim is made about immediate
 * rejection of an already-issued access JWT, which remains valid until it expires.
 */
@Testcontainers
class OidcRevocationKeycloakIT {

    private static final String KEYCLOAK_IMAGE_REFERENCE =
            "quay.io/keycloak/keycloak:26.7.1@sha256:"
                    + "f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6";
    private static final String REALM = "betufu-local";
    private static final String CLIENT_ID = "betufu-bff";
    private static final String USERNAME = "technical";
    private static final String REDIRECT_URI =
            "http://localhost:4200/login/oauth2/code/keycloak";
    private static final Path REALM_FILE =
            Path.of("..", "infra", "keycloak", "realm-betufu.json");
    private static final Path THEME_DIR =
            Path.of("..", "infra", "keycloak", "themes", "betufu");

    private static final Pattern FORM_ACTION =
            Pattern.compile("<form[^>]*\\saction=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHORIZATION_CODE =
            Pattern.compile("[?&]code=([^&]+)");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
                    DockerImageName.parse(KEYCLOAK_IMAGE_REFERENCE))
            // Both the import filename and the theme mount mirror infra/compose.yaml: the realm
            // sets loginTheme=betufu, so the theme must exist in the container or the login page
            // this test drives would not render as it does in the local environment.
            .withCopyFileToContainer(
                    MountableFile.forHostPath(REALM_FILE),
                    "/opt/keycloak/data/import/betufu-local-realm.json")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(THEME_DIR),
                    "/opt/keycloak/themes/betufu")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static String clientSecret;
    private static String password;
    private static String issuer;
    private static ClientRegistration registration;

    @BeforeAll
    static void resolveFixture() throws Exception {
        assertThat(REALM_FILE).as("realm import file").exists();

        JsonNode realm = JSON.readTree(Files.readString(REALM_FILE, StandardCharsets.UTF_8));
        clientSecret = firstMatching(realm.path("clients"), "clientId", CLIENT_ID)
                .path("secret").asText();
        password = firstMatching(realm.path("users"), "username", USERNAME)
                .path("credentials").path(0).path("value").asText();

        assertThat(clientSecret).as("client secret from the realm import").isNotBlank();
        assertThat(password).as("password from the realm import").isNotBlank();

        // Docker publishes the mapped port on IPv4 only. On Windows the JDK HttpClient resolves
        // "localhost" to ::1 first and fails to connect, while Spring's client falls back to
        // IPv4 — which would make discovery succeed and the flow below fail. Pin IPv4 so every
        // client in this test agrees, and so Keycloak echoes a consistent host in discovery.
        String host = "localhost".equals(KEYCLOAK.getHost()) ? "127.0.0.1" : KEYCLOAK.getHost();
        issuer = "http://" + host + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/" + REALM;
        registration = ClientRegistrations.fromIssuerLocation(issuer)
                .registrationId("keycloak")
                .clientId(CLIENT_ID)
                .clientSecret(clientSecret)
                .redirectUri(REDIRECT_URI)
                .build();
    }

    @Test
    void discoveryPublishesTheRevocationEndpoint() {
        Object endpoint = registration.getProviderDetails()
                .getConfigurationMetadata()
                .get(OidcRevocationClient.REVOCATION_ENDPOINT_METADATA);

        assertThat(endpoint)
                .as("revocation_endpoint must come from discovery, never a fixed URL")
                .isInstanceOf(String.class);
        assertThat((String) endpoint).startsWith(issuer);
    }

    @Test
    void revokedRefreshTokenIsRejectedWithInvalidGrant() throws Exception {
        OidcRevocationClient client = new OidcRevocationClient(RestClient.builder().build());

        String refreshToken = authorizationCodeRefreshToken();
        assertThat(refreshToken).isNotBlank();

        // Baseline: the grant works before revocation. Keycloak rotates the refresh token, so
        // the rotated value is the one that must be revoked.
        HttpResponse<String> baseline = refresh(refreshToken);
        assertThat(baseline.statusCode())
                .as("refresh must succeed before revocation")
                .isEqualTo(200);
        String rotated = JSON.readTree(baseline.body()).path("refresh_token").asText();
        assertThat(rotated).isNotBlank();

        client.revokeRefreshToken(registration, rotated);

        HttpResponse<String> afterRevocation = refresh(rotated);
        assertThat(afterRevocation.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(afterRevocation.body()).path("error").asText())
                .isEqualTo("invalid_grant");
    }

    @Test
    void repeatedRevocationConvergesWithoutFailing() throws Exception {
        OidcRevocationClient client = new OidcRevocationClient(RestClient.builder().build());
        String refreshToken = authorizationCodeRefreshToken();

        client.revokeRefreshToken(registration, refreshToken);

        assertThatCode(() -> client.revokeRefreshToken(registration, refreshToken))
                .as("RFC 7009 requires a repeated revocation to succeed")
                .doesNotThrowAnyException();

        assertThat(refresh(refreshToken).statusCode()).isEqualTo(400);
    }

    private static JsonNode firstMatching(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }

        throw new IllegalStateException(
                "Realm import does not declare " + field + "=" + value);
    }

    /**
     * Drives a real Authorization Code flow and returns the issued refresh token.
     *
     * <p>The realm's client enforces PKCE, so the request carries {@code code_challenge} and
     * {@code code_challenge_method=S256} and the token exchange carries the matching
     * {@code code_verifier}. Omitting them makes Keycloak reject the request with
     * {@code invalid_request}, which is the intended behaviour of the approved client.
     */
    private String authorizationCodeRefreshToken() throws Exception {
        // Cookies are echoed manually rather than through a CookieManager: the JDK cookie store
        // refuses to persist cookies for an IP-literal host, which would silently drop the
        // Keycloak auth-session cookie and make the credential POST land without a session.
        // NEVER, so an OAuth error redirect to the registered redirect_uri is inspected here
        // instead of being followed to a port nothing is listening on.
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String codeVerifier = randomCodeVerifier();
        String authorizationUri = registration.getProviderDetails().getAuthorizationUri()
                + "?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode("openid")
                + "&state=" + encode("revocation-it-state")
                + "&code_challenge=" + encode(codeChallenge(codeVerifier))
                + "&code_challenge_method=S256"
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> loginPage = http.send(
                HttpRequest.newBuilder(URI.create(authorizationUri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (loginPage.statusCode() == 302) {
            throw new IllegalStateException(
                    "Authorization request was rejected: "
                            + loginPage.headers().firstValue("Location").orElse("no Location"));
        }
        assertThat(loginPage.statusCode())
                .as("authorization endpoint at %s", authorizationUri)
                .isEqualTo(200);

        Matcher action = FORM_ACTION.matcher(loginPage.body());
        assertThat(action.find()).as("Keycloak login form action").isTrue();
        String formAction = action.group(1).replace("&amp;", "&");

        String credentials = "username=" + encode(USERNAME)
                + "&password=" + encode(password)
                + "&credentialId=";

        HttpResponse<String> submitted = http.send(
                HttpRequest.newBuilder(URI.create(formAction))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookieHeader(loginPage))
                        .POST(HttpRequest.BodyPublishers.ofString(credentials))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String location = submitted.headers().firstValue("Location").orElse("");
        assertThat(location)
                .as("login must redirect to the registered redirect_uri with a code; "
                        + "a required action on the user would break this flow")
                .startsWith(REDIRECT_URI);

        Matcher code = AUTHORIZATION_CODE.matcher(location);
        assertThat(code.find()).as("authorization code in redirect").isTrue();

        String body = "grant_type=authorization_code"
                + "&code=" + code.group(1)
                + "&code_verifier=" + encode(codeVerifier)
                + "&redirect_uri=" + encode(REDIRECT_URI);
        HttpResponse<String> tokenResponse = post(
                registration.getProviderDetails().getTokenUri(), body);
        assertThat(tokenResponse.statusCode()).isEqualTo(200);

        return JSON.readTree(tokenResponse.body()).path("refresh_token").asText();
    }

    private HttpResponse<String> refresh(String refreshToken) throws Exception {
        return post(
                registration.getProviderDetails().getTokenUri(),
                "grant_type=refresh_token&refresh_token=" + encode(refreshToken));
    }

    private HttpResponse<String> post(String uri, String body) throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(uri))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Authorization", "Basic " + basic)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Rebuilds a {@code Cookie} header from every {@code Set-Cookie} the response carried. */
    private static String cookieHeader(HttpResponse<?> response) {
        StringBuilder cookies = new StringBuilder();
        for (String setCookie : response.headers().allValues("set-cookie")) {
            int end = setCookie.indexOf(';');
            String pair = (end < 0) ? setCookie : setCookie.substring(0, end);
            if (pair.isBlank()) {
                continue;
            }
            if (!cookies.isEmpty()) {
                cookies.append("; ");
            }
            cookies.append(pair.trim());
        }

        return cookies.toString();
    }

    private static String randomCodeVerifier() {
        byte[] entropy = new byte[64];
        new java.security.SecureRandom().nextBytes(entropy);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    private static String codeChallenge(String codeVerifier) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));

        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
