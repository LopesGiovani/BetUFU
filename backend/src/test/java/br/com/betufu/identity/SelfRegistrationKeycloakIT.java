package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Proves self-registration with mandatory email verification against real Keycloak 26.7.1 and Mailpit.
 *
 * <p>Measures:
 * <ul>
 *   <li>AUTH-02: Self-registration through Keycloak's hosted page without BETUFU handling passwords.</li>
 *   <li>AUTH-03 & D-38 (A-2): With email confirmation pending, NO authorization code is emitted.</li>
 *   <li>AUTH-03: Technical user continues to authenticate after email verification is required in realm.</li>
 *   <li>A-11: ID token claims inspection under current requested OIDC scopes.</li>
 * </ul>
 */
@Testcontainers
class SelfRegistrationKeycloakIT {

    private static final String MAILPIT_IMAGE_REFERENCE =
            "docker.io/axllent/mailpit:v1.30.7@sha256:"
                    + "d5ecbb067db3705fa953d79e1b7f81ef84038df67aba6c52825d8c02a1ea748a";
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
    private static final Pattern REGISTRATION_LINK =
            Pattern.compile("href=\"([^\"]*login-actions/registration[^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_TOKEN_HREF =
            Pattern.compile("href=\"([^\"]*/realms/betufu-local/login-actions/action-token[^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_TOKEN_TEXT =
            Pattern.compile("(https?://[^\\s\"'<>]+/realms/betufu-local/login-actions/action-token[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
    private static final String NOTICE =
            "A BETUFU é uma plataforma independente e não possui vínculo oficial com a UFU.";
    private static final Pattern HTML_RESOURCE_TAG =
            Pattern.compile("<(link|script|img|source|base)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOURCE_ATTR =
            Pattern.compile("(?:href|src|srcset)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_TAG =
            Pattern.compile("<input\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_EXTRACTOR =
            Pattern.compile("([a-zA-Z0-9_-]+)(?:\\s*=\\s*\"([^\"]*)\"|\\s*=\\s*'([^']*)'|\\s*=\\s*([^\\s\"'=<>`]+))?", Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(
                    DockerImageName.parse(MAILPIT_IMAGE_REFERENCE))
            .withNetwork(NETWORK)
            .withNetworkAliases("mailpit")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages")
                    .forPort(8025)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
                    DockerImageName.parse(KEYCLOAK_IMAGE_REFERENCE))
            .withNetwork(NETWORK)
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
            .dependsOn(MAILPIT)
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

        boolean hasClientSecret = clientSecret != null && !clientSecret.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        assertThat(hasClientSecret).as("client secret from the realm import must be present").isTrue();
        assertThat(hasPassword).as("password from the realm import must be present").isTrue();

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
    void themeTemplateAllowlistContainsOnlyFooterFtl() throws Exception {
        Path themePath = THEME_DIR.toAbsolutePath().normalize();
        Path loginThemePath = themePath.resolve("login");
        boolean themeDirExists = Files.isDirectory(loginThemePath);
        assertThat(themeDirExists)
                .as("KEYCLOAK_THEME_TEMPLATE_ALLOWLIST: Login theme directory must exist")
                .isTrue();

        try (var stream = Files.walk(themePath)) {
            var ftlFiles = stream.filter(p -> p.toString().endsWith(".ftl"))
                    .map(p -> loginThemePath.relativize(p).toString().replace('\\', '/'))
                    .toList();
            boolean exactFtlList = ftlFiles.size() == 1 && ftlFiles.contains("footer.ftl");
            assertThat(exactFtlList)
                    .as("KEYCLOAK_THEME_TEMPLATE_ALLOWLIST: Theme must contain exactly footer.ftl as template override")
                    .isTrue();
        }
    }

    @Test
    void selfRegistrationEnforcesEmailVerificationBeforeIssuingAuthorizationCode() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String testEmail = "user-" + uniqueSuffix + "@betufu.local";
        String testPassword = "ValidPassword123!";

        // Step 1: Initiate authorization code flow and follow registration link
        String codeVerifier = randomCodeVerifier();
        String authorizationUri = registration.getProviderDetails().getAuthorizationUri()
                + "?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode("openid")
                + "&state=" + encode("self-registration-state")
                + "&code_challenge=" + encode(codeChallenge(codeVerifier))
                + "&code_challenge_method=S256"
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> loginPage = http.send(
                HttpRequest.newBuilder(URI.create(authorizationUri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThemeProperties(loginPage.body(), "Initial login page");

        String cookies = cookieHeader(loginPage);

        Matcher regLinkMatcher = REGISTRATION_LINK.matcher(loginPage.body());
        assertThat(regLinkMatcher.find())
                .as("KEYCLOAK_THEME_REGISTRATION_PAGE: Login page must render a registration link when registrationAllowed=true")
                .isTrue();

        String regHref = regLinkMatcher.group(1).replace("&amp;", "&");
        URI regUri = URI.create(authorizationUri).resolve(regHref);

        HttpResponse<String> regPage = http.send(
                HttpRequest.newBuilder(regUri)
                        .header("Cookie", cookies)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(regPage.statusCode()).isEqualTo(200);
        assertThemeProperties(regPage.body(), "Registration page");
        cookies = mergeCookies(cookies, regPage);

        // Prove password fields are not present on initial registration page
        String regBodyText = regPage.body() != null ? regPage.body() : "";
        boolean hasNoPasswordFields = !regBodyText.contains("name=\"password\"")
                && !regBodyText.contains("name=\"password-confirm\"");
        assertThat(hasNoPasswordFields)
                .as("KEYCLOAK_THEME_NO_INITIAL_PASSWORD: Registration page must not contain password fields in initial registration")
                .isTrue();

        List<String> editableInputs = extractEditableInputNames(regPage.body());
        boolean exactEditableInputs = editableInputs.size() == 1 && "email".equals(editableInputs.get(0));
        assertThat(exactEditableInputs)
                .as("KEYCLOAK_THEME_REGISTRATION_INPUTS: Registration page must contain exactly one editable user input: [email], found: " + editableInputs)
                .isTrue();

        Matcher regAction = FORM_ACTION.matcher(regPage.body());
        assertThat(regAction.find())
                .as("Registration page must contain a form action")
                .isTrue();
        String regFormAction = regAction.group(1).replace("&amp;", "&");
        URI regFormUri = regUri.resolve(regFormAction);

        // Submit initial registration with only email
        String regFormData = "email=" + encode(testEmail);

        HttpResponse<String> regSubmitted = http.send(
                HttpRequest.newBuilder(regFormUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookies)
                        .POST(HttpRequest.BodyPublishers.ofString(regFormData))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Step 2: Observe behavior with email confirmation pending (A-2 / D-38 measurement)
        String regLocation = regSubmitted.headers().firstValue("Location").orElse("");
        boolean regRedirectsToApp = regLocation.startsWith(REDIRECT_URI);
        assertThat(regRedirectsToApp)
                .as("With email confirmation pending, registration must NOT redirect to the application REDIRECT_URI")
                .isFalse();

        boolean regLocationHasCode = AUTHORIZATION_CODE.matcher(regLocation).find();
        assertThat(regLocationHasCode)
                .as("Redirect location must not contain an authorization code when email confirmation is pending")
                .isFalse();

        String regBody = regSubmitted.body() != null ? regSubmitted.body() : "";
        boolean regBodyHasCode = AUTHORIZATION_CODE.matcher(regBody).find();
        assertThat(regBodyHasCode)
                .as("Response body must not contain an authorization code when email confirmation is pending")
                .isFalse();

        URI expectedBaseUri = URI.create(issuerBaseUrl());
        String expectedScheme = expectedBaseUri.getScheme();
        String expectedAuthority = expectedBaseUri.getAuthority();

        // Follow internal Keycloak redirects manually until reaching status 200 on VERIFY_EMAIL required action
        URI currentRegFlowUri = regFormUri;
        HttpResponse<String> verifyEmailPage = regSubmitted;
        cookies = mergeCookies(cookies, regSubmitted);

        for (int i = 0; i < 10; i++) {
            if (verifyEmailPage.statusCode() >= 300 && verifyEmailPage.statusCode() < 400) {
                String loc = verifyEmailPage.headers().firstValue("Location").orElse("");
                boolean hasRedirectLocation = !loc.isBlank();
                assertThat(hasRedirectLocation)
                        .as("Redirect location must not be blank")
                        .isTrue();
                assertThat(loc.startsWith(REDIRECT_URI))
                        .as("Redirect location must not point to REDIRECT_URI")
                        .isFalse();
                assertThat(AUTHORIZATION_CODE.matcher(loc).find())
                        .as("Redirect location must not contain an authorization code")
                        .isFalse();

                URI resolvedLoc = currentRegFlowUri.resolve(loc);
                if (!expectedScheme.equalsIgnoreCase(resolvedLoc.getScheme())
                        || !expectedAuthority.equalsIgnoreCase(resolvedLoc.getAuthority())) {
                    throw new IllegalStateException("Redirect location diverted from expected Keycloak target scheme or authority");
                }
                currentRegFlowUri = resolvedLoc;

                verifyEmailPage = http.send(
                        HttpRequest.newBuilder(currentRegFlowUri)
                                .header("Cookie", cookies)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                cookies = mergeCookies(cookies, verifyEmailPage);

                String currentBody = verifyEmailPage.body() != null ? verifyEmailPage.body() : "";
                assertThat(AUTHORIZATION_CODE.matcher(currentBody).find())
                        .as("Response body must not contain an authorization code")
                        .isFalse();
                continue;
            }

            if (verifyEmailPage.statusCode() == 200) {
                break;
            }
            break;
        }

        assertThat(verifyEmailPage.statusCode())
                .as("Verify email page must return status 200")
                .isEqualTo(200);
        assertThemeProperties(verifyEmailPage.body(), "Verify email page");

        // Step 3: Await confirmation email in Mailpit and extract action token link
        String mailpitHost = "localhost".equals(MAILPIT.getHost()) ? "127.0.0.1" : MAILPIT.getHost();
        String mailpitLatestUrl = "http://" + mailpitHost + ":" + MAILPIT.getMappedPort(8025) + "/view/latest.html";

        String emailBody = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            HttpResponse<String> mailResponse = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitLatestUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (mailResponse.statusCode() == 200 && mailResponse.body() != null && !mailResponse.body().isBlank()) {
                emailBody = mailResponse.body();
                break;
            }
            Thread.sleep(500);
        }

        boolean emailReceived = emailBody != null && !emailBody.isBlank();
        assertThat(emailReceived)
                .as("Confirmation email must be received in Mailpit")
                .isTrue();

        String actionLink = extractActionLink(emailBody);
        URI rawActionUri = URI.create(actionLink);
        String actionPath = rawActionUri.getRawPath();
        String actionQuery = rawActionUri.getRawQuery();

        boolean validPath = actionPath != null
                && actionPath.equals("/realms/" + REALM + "/login-actions/action-token");
        boolean validQuery = actionQuery != null && !actionQuery.isBlank()
                && actionQuery.contains("key=");

        assertThat(validPath)
                .as("Action link path must be /realms/betufu-local/login-actions/action-token")
                .isTrue();
        assertThat(validQuery)
                .as("Action link query must be present and contain action token key")
                .isTrue();

        String actionPathAndQuery = actionPath + "?" + actionQuery;
        URI actionTargetUri = expectedBaseUri.resolve(actionPathAndQuery);

        // Follow action token link and navigate to UPDATE_PASSWORD form manually with Redirect.NEVER
        URI currentFlowUri = actionTargetUri;
        String actionFlowCookies = "";
        HttpResponse<String> flowPage = null;

        for (int i = 0; i < 10; i++) {
            flowPage = http.send(
                    HttpRequest.newBuilder(currentFlowUri)
                            .header("Cookie", actionFlowCookies)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            actionFlowCookies = mergeCookies(actionFlowCookies, flowPage);
            int statusCode = flowPage.statusCode();

            if (statusCode >= 300 && statusCode < 400) {
                String location = flowPage.headers().firstValue("Location").orElse("");
                if (location.isBlank()) {
                    break;
                }
                URI resolvedLoc = currentFlowUri.resolve(location);
                if (!expectedScheme.equalsIgnoreCase(resolvedLoc.getScheme())
                        || !expectedAuthority.equalsIgnoreCase(resolvedLoc.getAuthority())) {
                    throw new IllegalStateException("Redirect location diverted from expected Keycloak target scheme or authority");
                }
                currentFlowUri = resolvedLoc;
                continue;
            }

            if (statusCode == 200) {
                String body = flowPage.body() != null ? flowPage.body() : "";
                if (body.contains("password-new") || body.contains("name=\"password-confirm\"") || body.contains("name=\"password\"")) {
                    break;
                }

                Matcher actionHrefMatcher = ACTION_TOKEN_HREF.matcher(body);
                if (actionHrefMatcher.find()) {
                    String nextHref = actionHrefMatcher.group(1).replace("&amp;", "&");
                    URI resolvedHref = currentFlowUri.resolve(nextHref);
                    if (!expectedScheme.equalsIgnoreCase(resolvedHref.getScheme())
                            || !expectedAuthority.equalsIgnoreCase(resolvedHref.getAuthority())) {
                        throw new IllegalStateException("Action token link diverted from expected Keycloak target scheme or authority");
                    }
                    currentFlowUri = resolvedHref;
                    continue;
                }

                Matcher formMatcher = FORM_ACTION.matcher(body);
                if (formMatcher.find()) {
                    String nextAction = formMatcher.group(1).replace("&amp;", "&");
                    URI formResolved = currentFlowUri.resolve(nextAction);
                    if (!expectedScheme.equalsIgnoreCase(formResolved.getScheme())
                            || !expectedAuthority.equalsIgnoreCase(formResolved.getAuthority())) {
                        throw new IllegalStateException("Form action diverted from expected Keycloak target scheme or authority");
                    }
                    currentFlowUri = formResolved;
                    flowPage = http.send(
                            HttpRequest.newBuilder(currentFlowUri)
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .header("Cookie", actionFlowCookies)
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
                    actionFlowCookies = mergeCookies(actionFlowCookies, flowPage);
                    if (flowPage.statusCode() >= 300 && flowPage.statusCode() < 400) {
                        String loc = flowPage.headers().firstValue("Location").orElse("");
                        if (loc.isBlank()) {
                            break;
                        }
                        URI locResolved = currentFlowUri.resolve(loc);
                        if (!expectedScheme.equalsIgnoreCase(locResolved.getScheme())
                                || !expectedAuthority.equalsIgnoreCase(locResolved.getAuthority())) {
                            throw new IllegalStateException("Redirect location diverted from expected Keycloak target scheme or authority");
                        }
                        currentFlowUri = locResolved;
                        continue;
                    }
                }
                break;
            }
            break;
        }

        boolean hasFlowPage = flowPage != null;
        assertThat(hasFlowPage).as("Action token navigation must reach a page").isTrue();
        assertThat(flowPage.statusCode()).as("Action token navigation must return status 200").isEqualTo(200);
        assertThemeProperties(flowPage.body(), "Update password page");

        String flowBody = flowPage.body() != null ? flowPage.body() : "";
        boolean hasPasswordFields = flowBody.contains("password-new")
                && flowBody.contains("password-confirm");
        assertThat(hasPasswordFields)
                .as("Update password form must contain password-new and password-confirm fields")
                .isTrue();

        Matcher updatePwdActionMatcher = FORM_ACTION.matcher(flowPage.body());
        assertThat(updatePwdActionMatcher.find()).as("Update password page must contain a form action").isTrue();
        String updatePwdFormAction = updatePwdActionMatcher.group(1).replace("&amp;", "&");
        URI updatePwdFormUri = currentFlowUri.resolve(updatePwdFormAction);
        if (!expectedScheme.equalsIgnoreCase(updatePwdFormUri.getScheme())
                || !expectedAuthority.equalsIgnoreCase(updatePwdFormUri.getAuthority())) {
            throw new IllegalStateException("Update password form action diverted from expected Keycloak target scheme or authority");
        }

        String updatePwdData = "password-new=" + encode(testPassword)
                + "&password-confirm=" + encode(testPassword);

        HttpResponse<String> pwdSubmitResponse = http.send(
                HttpRequest.newBuilder(updatePwdFormUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", actionFlowCookies)
                        .POST(HttpRequest.BodyPublishers.ofString(updatePwdData))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(pwdSubmitResponse.statusCode())
                .as("Update password submission must succeed (status 200, 302, or 303)")
                .isIn(200, 302, 303);

        // Step 4: Perform a brand new authentication flow to prove real login
        String codeVerifierConfirmed = randomCodeVerifier();
        String authUriConfirmed = registration.getProviderDetails().getAuthorizationUri()
                + "?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode("openid")
                + "&state=" + encode("confirmed-login-state")
                + "&code_challenge=" + encode(codeChallenge(codeVerifierConfirmed))
                + "&code_challenge_method=S256"
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> loginPageConfirmed = http.send(
                HttpRequest.newBuilder(URI.create(authUriConfirmed)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (loginPageConfirmed.statusCode() != 200) {
            throw new IllegalStateException("Authorization request did not return 200 OK");
        }
        assertThat(loginPageConfirmed.statusCode()).isEqualTo(200);
        assertThemeProperties(loginPageConfirmed.body(), "Confirmed login page");

        Matcher loginActionMatcher = FORM_ACTION.matcher(loginPageConfirmed.body());
        assertThat(loginActionMatcher.find()).as("Keycloak login form action").isTrue();
        String loginFormAction = loginActionMatcher.group(1).replace("&amp;", "&");
        URI loginFormUri = URI.create(authUriConfirmed).resolve(loginFormAction);

        String loginCredentials = "username=" + encode(testEmail)
                + "&password=" + encode(testPassword)
                + "&credentialId=";

        HttpResponse<String> loginSubmitted = http.send(
                HttpRequest.newBuilder(loginFormUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookieHeader(loginPageConfirmed))
                        .POST(HttpRequest.BodyPublishers.ofString(loginCredentials))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(loginSubmitted.statusCode())
                .as("Confirmed user login must redirect (302) on success")
                .isEqualTo(302);

        String loginLocation = loginSubmitted.headers().firstValue("Location").orElse("");
        boolean loginRedirectsToApp = loginLocation.startsWith(REDIRECT_URI);
        assertThat(loginRedirectsToApp)
                .as("Confirmed user login must redirect to REDIRECT_URI with authorization code")
                .isTrue();

        Matcher authCodeMatcher = AUTHORIZATION_CODE.matcher(loginLocation);
        boolean hasAuthCode = authCodeMatcher.find();
        assertThat(hasAuthCode)
                .as("Authorization code must be present in redirect URI after email confirmation")
                .isTrue();
        String authorizationCode = authCodeMatcher.group(1);

        // Step 5: Exchange code for tokens & inspect ID token claims (A-11)
        String tokenBody = "grant_type=authorization_code"
                + "&code=" + encode(authorizationCode)
                + "&code_verifier=" + encode(codeVerifierConfirmed)
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> tokenResponse = postClientToken(
                registration.getProviderDetails().getTokenUri(), tokenBody);
        assertThat(tokenResponse.statusCode()).isEqualTo(200);

        JsonNode tokenJson = JSON.readTree(tokenResponse.body());
        boolean hasIdToken = tokenJson.hasNonNull("id_token");
        assertThat(hasIdToken).as("id_token must be present in token response").isTrue();

        String idTokenJwt = tokenJson.path("id_token").asText();
        String[] jwtParts = idTokenJwt.split("\\.");
        assertThat(jwtParts.length).as("JWT must contain header, payload, signature").isGreaterThanOrEqualTo(2);
        String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
        JsonNode idTokenClaims = JSON.readTree(payloadJson);

        boolean issMatches = issuer.equals(idTokenClaims.path("iss").asText());
        assertThat(issMatches)
                .as("iss claim must match issuer")
                .isTrue();
        assertThat(idTokenClaims.has("sub"))
                .as("ID token must contain subject claim")
                .isTrue();

        String registeredUserId = idTokenClaims.path("sub").asText();
        boolean hasRegisteredUserId = registeredUserId != null && !registeredUserId.isBlank();
        assertThat(hasRegisteredUserId).as("Registered user ID must not be blank").isTrue();

        // Step 6: Safe assertions on user state via Admin API
        String adminToken = fetchAdminToken(http);
        String userEndpoint = issuerBaseUrl() + "/admin/realms/" + REALM + "/users/" + registeredUserId;
        HttpResponse<String> userResponse = http.send(
                HttpRequest.newBuilder(URI.create(userEndpoint))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(userResponse.statusCode()).isEqualTo(200);

        JsonNode userJson = JSON.readTree(userResponse.body());
        assertThat(userJson.path("enabled").asBoolean(false))
                .as("User must be enabled after registration and confirmation")
                .isTrue();
        assertThat(userJson.path("emailVerified").asBoolean(false))
                .as("Email must be verified after confirmation")
                .isTrue();
        assertThat(userJson.path("requiredActions").isEmpty())
                .as("Required actions must be empty after completing UPDATE_PASSWORD and email verification")
                .isTrue();
        boolean usernameMatchesEmail = testEmail.equals(userJson.path("username").asText());
        assertThat(usernameMatchesEmail)
                .as("Username must match registered email when registrationEmailAsUsername=true")
                .isTrue();
        boolean userEmailMatches = testEmail.equals(userJson.path("email").asText());
        assertThat(userEmailMatches)
                .as("Email must match registered email")
                .isTrue();

        // Step 7: A-11 empirical measurement verification
        boolean hasEmail = idTokenClaims.has("email");
        boolean hasEmailVerified = idTokenClaims.has("email_verified");
        assertThat(hasEmail)
                .as("ID token must contain email claim under current requested scope")
                .isTrue();
        assertThat(hasEmailVerified)
                .as("ID token must contain email_verified claim under current requested scope")
                .isTrue();
    }

    @Test
    void technicalUserAuthenticatesSuccessfullyWithVerifiedEmail() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String codeVerifier = randomCodeVerifier();
        String authorizationUri = registration.getProviderDetails().getAuthorizationUri()
                + "?response_type=code"
                + "&client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode("openid")
                + "&state=" + encode("technical-auth-state")
                + "&code_challenge=" + encode(codeChallenge(codeVerifier))
                + "&code_challenge_method=S256"
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> loginPage = http.send(
                HttpRequest.newBuilder(URI.create(authorizationUri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (loginPage.statusCode() != 200) {
            throw new IllegalStateException(
                    "Authorization request did not return 200 OK (status: " + loginPage.statusCode() + ")");
        }
        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThemeProperties(loginPage.body(), "Technical login page");

        Matcher action = FORM_ACTION.matcher(loginPage.body());
        assertThat(action.find()).as("Keycloak login form action").isTrue();
        URI formUri = URI.create(authorizationUri).resolve(action.group(1).replace("&amp;", "&"));

        String credentials = "username=" + encode(USERNAME)
                + "&password=" + encode(password)
                + "&credentialId=";

        HttpResponse<String> submitted = http.send(
                HttpRequest.newBuilder(formUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookieHeader(loginPage))
                        .POST(HttpRequest.BodyPublishers.ofString(credentials))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(submitted.statusCode())
                .as("Technical login form submission must redirect (302) on success")
                .isEqualTo(302);

        String location = submitted.headers().firstValue("Location").orElse("");
        boolean technicalRedirectsToApp = location.startsWith(REDIRECT_URI);
        assertThat(technicalRedirectsToApp)
                .as("Technical user with emailVerified=true must redirect to REDIRECT_URI with code")
                .isTrue();

        Matcher code = AUTHORIZATION_CODE.matcher(location);
        boolean technicalHasCode = code.find();
        assertThat(technicalHasCode).as("authorization code in redirect").isTrue();

        String tokenBody = "grant_type=authorization_code"
                + "&code=" + encode(code.group(1))
                + "&code_verifier=" + encode(codeVerifier)
                + "&redirect_uri=" + encode(REDIRECT_URI);
        HttpResponse<String> tokenResponse = postClientToken(
                registration.getProviderDetails().getTokenUri(), tokenBody);
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
    }

    private String fetchAdminToken(HttpClient http) throws Exception {
        String tokenEndpoint = issuerBaseUrl() + "/realms/master/protocol/openid-connect/token";
        String body = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=admin"
                + "&password=admin-test-only";

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(tokenEndpoint))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).path("access_token").asText();
    }

    private static String extractActionLink(String emailContent) {
        if (emailContent == null || emailContent.isBlank()) {
            throw new IllegalStateException("Email content is empty or null");
        }

        Matcher hrefMatcher = ACTION_TOKEN_HREF.matcher(emailContent);
        if (hrefMatcher.find()) {
            return hrefMatcher.group(1).replace("&amp;", "&");
        }

        Matcher textMatcher = ACTION_TOKEN_TEXT.matcher(emailContent);
        if (textMatcher.find()) {
            return textMatcher.group(1).replace("&amp;", "&");
        }

        throw new IllegalStateException(
                "Could not extract action-token link for realm " + REALM + " from confirmation email");
    }

    private HttpResponse<String> postClientToken(String uri, String body) throws Exception {
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

    private static String issuerBaseUrl() {
        return issuer.substring(0, issuer.indexOf("/realms/"));
    }

    private static JsonNode firstMatching(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }

        throw new IllegalStateException(
                "Realm import does not declare matching entry for " + field);
    }

    private static String cookieHeader(HttpResponse<?> response) {
        return mergeCookies("", response);
    }

    private static String mergeCookies(String previousCookies, HttpResponse<?> response) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (previousCookies != null && !previousCookies.isBlank()) {
            for (String part : previousCookies.split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    cookies.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
        }
        for (String setCookie : response.headers().allValues("set-cookie")) {
            int end = setCookie.indexOf(';');
            String pair = (end < 0) ? setCookie : setCookie.substring(0, end);
            int eq = pair.indexOf('=');
            if (eq > 0) {
                cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private static String randomCodeVerifier() {
        byte[] entropy = new byte[64];
        new SecureRandom().nextBytes(entropy);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    private static String codeChallenge(String codeVerifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));

        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void assertThemeProperties(String html, String pageDescription) {
        boolean notBlank = html != null && !html.isBlank();
        assertThat(notBlank)
                .as(pageDescription + " body must not be blank")
                .isTrue();

        int noticeCount = countOccurrences(html, NOTICE);
        assertThat(noticeCount)
                .as(pageDescription + " must render independence notice exactly once")
                .isEqualTo(1);

        boolean noticeInClass = html.contains("betufu-independence-notice");
        assertThat(noticeInClass)
                .as(pageDescription + " must contain betufu-independence-notice class")
                .isTrue();

        boolean hasThemeCss = html.contains("/login/betufu/css/login.css");
        assertThat(hasThemeCss)
                .as(pageDescription + " must reference betufu login.css")
                .isTrue();

        boolean allSameOrigin = checkAllResourcesSameOrigin(html);
        assertThat(allSameOrigin)
                .as(pageDescription + " all referenced resources must be same-origin")
                .isTrue();
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static List<String> extractEditableInputNames(String html) {
        List<String> names = new ArrayList<>();
        if (html == null) {
            return names;
        }
        Matcher tagMatcher = INPUT_TAG.matcher(html);
        while (tagMatcher.find()) {
            String attrBlock = tagMatcher.group(1);
            Map<String, String> attrs = new LinkedHashMap<>();
            Matcher attrMatcher = ATTR_EXTRACTOR.matcher(attrBlock);
            while (attrMatcher.find()) {
                String key = attrMatcher.group(1).toLowerCase();
                String val = attrMatcher.group(2) != null ? attrMatcher.group(2)
                        : attrMatcher.group(3) != null ? attrMatcher.group(3)
                        : attrMatcher.group(4) != null ? attrMatcher.group(4) : "";
                attrs.put(key, val);
            }
            String type = attrs.getOrDefault("type", "text").toLowerCase();
            if ("hidden".equals(type) || "submit".equals(type) || "button".equals(type)) {
                continue;
            }
            String name = attrs.get("name");
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static boolean checkAllResourcesSameOrigin(String html) {
        if (html == null) {
            return true;
        }
        URI keycloakBase = URI.create(issuerBaseUrl());
        String expectedScheme = keycloakBase.getScheme();
        String expectedAuthority = keycloakBase.getAuthority();

        Matcher tagMatcher = HTML_RESOURCE_TAG.matcher(html);
        while (tagMatcher.find()) {
            String attrs = tagMatcher.group(2);
            Matcher attrMatcher = RESOURCE_ATTR.matcher(attrs);
            while (attrMatcher.find()) {
                String candidate = attrMatcher.group(1).trim();
                for (String entry : candidate.split(",")) {
                    String uri = entry.trim().split("\\s+", 2)[0].trim();
                    if (uri.isEmpty() || uri.startsWith("#")) {
                        continue;
                    }
                    if (uri.regionMatches(true, 0, "data:", 0, 5)
                            || uri.regionMatches(true, 0, "blob:", 0, 5)) {
                        continue;
                    }
                    if (uri.startsWith("//")
                            || uri.regionMatches(true, 0, "javascript:", 0, 11)) {
                        return false;
                    }
                    try {
                        URI parsed = URI.create(uri);
                        if (parsed.getUserInfo() != null) {
                            return false;
                        }
                        if (parsed.isAbsolute()) {
                            String scheme = parsed.getScheme();
                            if (scheme == null) {
                                return false;
                            }
                            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                                return false;
                            }
                            if (!expectedScheme.equalsIgnoreCase(scheme)) {
                                return false;
                            }
                            String authority = parsed.getAuthority();
                            if (authority == null || !expectedAuthority.equalsIgnoreCase(authority)) {
                                return false;
                            }
                        } else {
                            if (parsed.getScheme() != null || parsed.getAuthority() != null) {
                                return false;
                            }
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
