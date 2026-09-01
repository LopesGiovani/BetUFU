package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Medição empírica A-3 (D-40): avalia se o Keycloak 26.7.1 produz gatilho observável (encerramento
 * de sessões SSO ou envio de OIDC Back-Channel Logout) ao redefinir a senha do usuário.
 *
 * <p>Responde empiricamente às quatro perguntas de A-3:
 *
 * <ol>
 *   <li>A sessão de SSO da pessoa no provedor sobreviveu à redefinição de senha?
 *   <li>Com o canal de retaguarda habilitado no cliente, chega alguma requisição de volta para a
 *       aplicação?
 *   <li>Se algo chegou, o conteúdo casa com o que o registro em memória guardou?
 *   <li>Existe alguma configuração de realm ou de cliente que faça a redefinição encerrar as
 *       sessões?
 * </ol>
 */
@Testcontainers
class PasswordResetTriggerSpikeIT {

  private static final String MAILPIT_IMAGE_REFERENCE =
      "docker.io/axllent/mailpit:v1.30.7@sha256:"
          + "d5ecbb067db3705fa953d79e1b7f81ef84038df67aba6c52825d8c02a1ea748a";
  private static final String KEYCLOAK_IMAGE_REFERENCE =
      "quay.io/keycloak/keycloak:26.7.1@sha256:"
          + "f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6";
  private static final String REALM = "betufu-local";
  private static final String CLIENT_ID = "betufu-bff";
  private static final String REDIRECT_URI = "http://localhost:4200/login/oauth2/code/keycloak";
  private static final Path REALM_FILE = Path.of("..", "infra", "keycloak", "realm-betufu.json");
  private static final Path THEME_DIR = Path.of("..", "infra", "keycloak", "themes", "betufu");

  private static final Pattern FORM_ACTION =
      Pattern.compile("<form[^>]*\\saction=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern AUTHORIZATION_CODE = Pattern.compile("[?&]code=([^&]+)");
  private static final Pattern RESET_CREDENTIALS_LINK =
      Pattern.compile(
          "href=\"([^\"]*login-actions/reset-credentials[^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern ACTION_TOKEN_HREF =
      Pattern.compile(
          "href=\"([^\"]*/realms/betufu-local/login-actions/action-token[^\"]*)\"",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ACTION_TOKEN_TEXT =
      Pattern.compile(
          "(https?://[^\\s\"'<>]+/realms/betufu-local/login-actions/action-token[^\\s\"'<>]+)",
          Pattern.CASE_INSENSITIVE);

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Network NETWORK = Network.newNetwork();

  private static final HttpServer BACKCHANNEL_SERVER;
  private static final int BACKCHANNEL_PORT;
  private static final List<ReceivedBackchannelRequest> RECEIVED_BACKCHANNEL_REQUESTS =
      new CopyOnWriteArrayList<>();

  record ReceivedBackchannelRequest(
      String method, Map<String, List<String>> headers, String body) {}

  static {
    try {
      BACKCHANNEL_SERVER = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
      BACKCHANNEL_PORT = BACKCHANNEL_SERVER.getAddress().getPort();
      org.testcontainers.Testcontainers.exposeHostPorts(BACKCHANNEL_PORT);

      BACKCHANNEL_SERVER.createContext(
          "/logout/connect/back-channel/keycloak",
          exchange -> {
            try {
              byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
              String body = new String(bodyBytes, StandardCharsets.UTF_8);
              Map<String, List<String>> headers = new LinkedHashMap<>();
              exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, new ArrayList<>(v)));
              RECEIVED_BACKCHANNEL_REQUESTS.add(
                  new ReceivedBackchannelRequest(exchange.getRequestMethod(), headers, body));
              exchange.sendResponseHeaders(200, -1);
            } catch (Throwable t) {
              exchange.sendResponseHeaders(500, -1);
            } finally {
              exchange.close();
            }
          });
      BACKCHANNEL_SERVER.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Container
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse(MAILPIT_IMAGE_REFERENCE))
          .withNetwork(NETWORK)
          .withNetworkAliases("mailpit")
          .withExposedPorts(1025, 8025)
          .waitingFor(
              Wait.forHttp("/api/v1/messages")
                  .forPort(8025)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE_REFERENCE))
          .withNetwork(NETWORK)
          .withAccessToHost(true)
          .withCopyFileToContainer(
              MountableFile.forHostPath(REALM_FILE),
              "/opt/keycloak/data/import/betufu-local-realm.json")
          .withCopyFileToContainer(
              MountableFile.forHostPath(THEME_DIR), "/opt/keycloak/themes/betufu")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only")
          .withCommand("start-dev", "--import-realm")
          .withExposedPorts(8080)
          .dependsOn(MAILPIT)
          .waitingFor(
              Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                  .forPort(8080)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  private static String clientSecret;
  private static String issuer;
  private static ClientRegistration registration;

  @BeforeAll
  static void resolveFixture() throws Exception {
    assertThat(REALM_FILE).as("realm import file").exists();

    JsonNode realm = JSON.readTree(Files.readString(REALM_FILE, StandardCharsets.UTF_8));
    clientSecret =
        firstMatching(realm.path("clients"), "clientId", CLIENT_ID).path("secret").asText();

    assertThat(clientSecret).as("client secret from the realm import must be present").isNotBlank();

    String host = "localhost".equals(KEYCLOAK.getHost()) ? "127.0.0.1" : KEYCLOAK.getHost();
    issuer = "http://" + host + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/" + REALM;
    registration =
        ClientRegistrations.fromIssuerLocation(issuer)
            .registrationId("keycloak")
            .clientId(CLIENT_ID)
            .clientSecret(clientSecret)
            .redirectUri(REDIRECT_URI)
            .build();

    // Configura atributos de canal de retaguarda no cliente betufu-bff dentro do container
    HttpClient http = HttpClient.newHttpClient();
    configureClientBackchannelLogout(http);
  }

  @AfterAll
  static void tearDown() {
    if (BACKCHANNEL_SERVER != null) {
      BACKCHANNEL_SERVER.stop(0);
    }
  }

  @Test
  void spikeMeasuresPasswordResetPreservesUserSessionAndEmitsNoBackchannelLogout()
      throws Exception {
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    String testEmail = "spike-user-" + uniqueSuffix + "@betufu.local";
    String initialPassword = "InitialPassword123!";
    String newPassword = "NewPassword123!";

    // Cria usuário de teste com e-mail verificado e credencial inicial
    String userId = createUserInKeycloak(http, testEmail, initialPassword);

    // Autentica via Authorization Code Flow para estabelecer sessão de SSO real no Keycloak
    String initialRefreshToken = executeAuthCodeLogin(http, testEmail, initialPassword);
    assertThat(initialRefreshToken).as("Initial refresh token must be issued").isNotBlank();

    // Consulta sessões ativas do usuário antes da redefinição (Pergunta 1 - Linha de base)
    JsonNode sessionsBefore = getUserSessions(http, userId);
    assertThat(sessionsBefore.isArray()).isTrue();
    assertThat(sessionsBefore.size()).as("SSO session count before password reset").isEqualTo(1);
    String originalSessionId = sessionsBefore.get(0).path("id").asText();
    assertThat(originalSessionId).as("Original session ID must be present").isNotBlank();

    // Limpa histórico do receptor de canal de retaguarda antes da redefinição
    RECEIVED_BACKCHANNEL_REQUESTS.clear();

    // Executa redefinição de senha pelo fluxo hospedado (AUTH-04) via reset-credentials e token de
    // ação Mailpit
    executeResetCredentialsFlow(http, testEmail, newPassword);

    // Medição Pergunta 1: A sessão de SSO sobreviveu à redefinição?
    JsonNode sessionsAfter = getUserSessions(http, userId);
    assertThat(sessionsAfter.isArray()).isTrue();
    assertThat(sessionsAfter.size())
        .as("SSO session count after password reset (Q1 measurement: SSO session survives)")
        .isEqualTo(1);
    String sessionAfterId = sessionsAfter.get(0).path("id").asText();
    assertThat(sessionAfterId)
        .as("SSO session ID is unchanged after password reset")
        .isEqualTo(originalSessionId);

    // Medição complementar de Q1: Refresh token anterior permanece funcional após redefinição
    HttpResponse<String> refreshResponse = refresh(initialRefreshToken);
    assertThat(refreshResponse.statusCode())
        .as("Refresh token issued before password reset remains functional (status 200)")
        .isEqualTo(200);

    // Medição Pergunta 2: Com o canal de retaguarda habilitado no cliente, chega alguma requisição
    // na redefinição?
    assertNoBackchannelRequests(Duration.ofSeconds(10));

    // Controle discriminante (Pergunta 3 e rejeição de prova vazia):
    // Encerramento explícito de sessão via Admin API dispara o canal de retaguarda e entrega
    // logout_token
    deleteUserSessions(http, userId);

    JsonNode sessionsAfterDelete = getUserSessions(http, userId);
    assertThat(sessionsAfterDelete.size())
        .as("SSO session count after explicit administrative termination")
        .isEqualTo(0);

    HttpResponse<String> refreshAfterDelete = refresh(initialRefreshToken);
    assertThat(refreshAfterDelete.statusCode())
        .as("Refresh token is rejected after session termination (status 400 invalid_grant)")
        .isEqualTo(400);

    // Aguarda notificação de canal de retaguarda gerada pelo encerramento administrativo
    awaitBackchannelRequests(1, Duration.ofSeconds(10));
    assertThat(RECEIVED_BACKCHANNEL_REQUESTS)
        .as("Back-channel logout received on explicit session termination (discriminating control)")
        .hasSize(1);

    ReceivedBackchannelRequest received = RECEIVED_BACKCHANNEL_REQUESTS.get(0);
    assertThat(received.method()).isEqualTo("POST");
    assertThat(received.body()).contains("logout_token=");

    String logoutToken = extractFormParam(received.body(), "logout_token");
    assertThat(logoutToken).isNotBlank();

    String[] jwtParts = logoutToken.split("\\.");
    assertThat(jwtParts.length).isGreaterThanOrEqualTo(2);
    String payloadJson =
        new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
    JsonNode logoutClaims = JSON.readTree(payloadJson);

    assertThat(logoutClaims.path("iss").asText()).as("Logout token iss claim").isEqualTo(issuer);
    assertThat(logoutClaims.path("sub").asText()).as("Logout token sub claim").isEqualTo(userId);
    assertThat(logoutClaims.has("sid"))
        .as("Logout token sid claim must be absent in user-level logout")
        .isFalse();
    assertThat(
            logoutClaims.path("events").has("http://schemas.openid.net/event/backchannel-logout"))
        .as("Logout token events claim contains backchannel-logout event")
        .isTrue();
    assertThat(logoutClaims.has("nonce")).as("Logout token must not contain nonce claim").isFalse();
  }

  @Test
  void spikeEnumeratesKeycloakRealmAndFlowConfigurationsForSessionTermination() throws Exception {
    HttpClient http = HttpClient.newBuilder().build();

    // Medição Pergunta 4: Inspeciona opções de configuração expostas pelo realm e pelo fluxo de
    // redefinição
    JsonNode realmRep = getRealmRepresentation(http);
    assertThat(realmRep.has("ssoSessionIdleTimeout")).isTrue();
    assertThat(realmRep.has("ssoSessionMaxLifespan")).isTrue();
    assertThat(realmRep.path("revokeRefreshToken").asBoolean(false)).isFalse();
    assertThat(realmRep.path("notBefore").asInt(0)).isEqualTo(0);

    JsonNode flows = getAuthenticationFlows(http);
    assertThat(flows.isArray()).isTrue();

    JsonNode resetFlow = null;
    for (JsonNode flow : flows) {
      String alias = flow.path("alias").asText("");
      if ("reset credentials".equalsIgnoreCase(alias)
          || "reset-credentials".equalsIgnoreCase(alias)) {
        resetFlow = flow;
        break;
      }
    }
    assertThat(resetFlow).as("Reset credentials flow must exist in realm flows").isNotNull();
    String actualAlias = resetFlow.path("alias").asText();

    JsonNode executions = getFlowExecutions(http, actualAlias);
    assertThat(executions.isArray()).isTrue();

    List<String> providers = new ArrayList<>();
    executions.forEach(
        e -> {
          if (e.hasNonNull("providerId")) {
            providers.add(e.path("providerId").asText());
          }
        });

    assertThat(providers)
        .as("Built-in executions for reset credentials flow")
        .contains("reset-credentials-choose-user", "reset-credential-email", "reset-password");

    // Nenhuma das execuções padrão do fluxo de redefinição invalida sessões de SSO
    boolean hasSessionRevocationExecution =
        providers.stream()
            .anyMatch(
                p -> p.toLowerCase().contains("session") || p.toLowerCase().contains("logout"));
    assertThat(hasSessionRevocationExecution)
        .as("Keycloak 26.7.1 reset credentials flow does not contain session revocation execution")
        .isFalse();
  }

  private static String createUserInKeycloak(HttpClient http, String email, String password)
      throws Exception {
    String adminToken = fetchAdminToken(http);
    String usersUrl = issuerBaseUrl() + "/admin/realms/" + REALM + "/users";

    ObjectNode userNode = JSON.createObjectNode();
    userNode.put("username", email);
    userNode.put("email", email);
    userNode.put("enabled", true);
    userNode.put("emailVerified", true);
    ObjectNode credential = JSON.createObjectNode();
    credential.put("type", "password");
    credential.put("value", password);
    credential.put("temporary", false);
    userNode.putArray("credentials").add(credential);

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(usersUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(userNode)))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    String location = response.headers().firstValue("Location").orElseThrow();
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private static JsonNode getUserSessions(HttpClient http, String userId) throws Exception {
    String adminToken = fetchAdminToken(http);
    String sessionsUrl =
        issuerBaseUrl() + "/admin/realms/" + REALM + "/users/" + userId + "/sessions";

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(sessionsUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private static void deleteUserSessions(HttpClient http, String userId) throws Exception {
    String adminToken = fetchAdminToken(http);
    String logoutUrl = issuerBaseUrl() + "/admin/realms/" + REALM + "/users/" + userId + "/logout";

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(logoutUrl))
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(204);
  }

  private static JsonNode getRealmRepresentation(HttpClient http) throws Exception {
    String adminToken = fetchAdminToken(http);
    String realmUrl = issuerBaseUrl() + "/admin/realms/" + REALM;

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(realmUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private static JsonNode getAuthenticationFlows(HttpClient http) throws Exception {
    String adminToken = fetchAdminToken(http);
    String flowsUrl = issuerBaseUrl() + "/admin/realms/" + REALM + "/authentication/flows";

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(flowsUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private static JsonNode getFlowExecutions(HttpClient http, String flowAlias) throws Exception {
    String adminToken = fetchAdminToken(http);
    String encodedAlias = URLEncoder.encode(flowAlias, StandardCharsets.UTF_8).replace("+", "%20");
    String flowUrl =
        issuerBaseUrl()
            + "/admin/realms/"
            + REALM
            + "/authentication/flows/"
            + encodedAlias
            + "/executions";

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(flowUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private static void configureClientBackchannelLogout(HttpClient http) throws Exception {
    String adminToken = fetchAdminToken(http);
    String clientsUrl =
        issuerBaseUrl() + "/admin/realms/" + REALM + "/clients?clientId=" + encode(CLIENT_ID);

    HttpResponse<String> listResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(clientsUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(listResponse.statusCode()).isEqualTo(200);
    JsonNode clients = JSON.readTree(listResponse.body());
    assertThat(clients.size()).isGreaterThan(0);
    String clientDbId = clients.get(0).path("id").asText();

    String clientUrl = issuerBaseUrl() + "/admin/realms/" + REALM + "/clients/" + clientDbId;
    HttpResponse<String> clientResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(clientUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(clientResponse.statusCode()).isEqualTo(200);
    ObjectNode clientRep = (ObjectNode) JSON.readTree(clientResponse.body());

    JsonNode attributesNode = clientRep.path("attributes");
    ObjectNode attributes;
    if (attributesNode.isObject()) {
      attributes = (ObjectNode) attributesNode;
    } else {
      attributes = clientRep.putObject("attributes");
    }

    String backchannelUrl =
        "http://host.testcontainers.internal:"
            + BACKCHANNEL_PORT
            + "/logout/connect/back-channel/keycloak";
    attributes.put("backchannel.logout.url", backchannelUrl);
    attributes.put("backchannel.logout.session-required", "true");
    attributes.put("backchannel.logout.revoke-offline-tokens", "false");

    HttpResponse<String> updateResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(clientUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminToken)
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(clientRep)))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(updateResponse.statusCode()).isIn(200, 204);

    HttpResponse<String> verifyResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(clientUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(verifyResponse.statusCode()).isEqualTo(200);
    JsonNode updatedClient = JSON.readTree(verifyResponse.body());
    assertThat(updatedClient.path("attributes").path("backchannel.logout.url").asText())
        .isEqualTo(backchannelUrl);
  }

  private static String executeAuthCodeLogin(HttpClient http, String email, String password)
      throws Exception {
    String codeVerifier = randomCodeVerifier();
    String authUri =
        registration.getProviderDetails().getAuthorizationUri()
            + "?response_type=code"
            + "&client_id="
            + encode(CLIENT_ID)
            + "&scope="
            + encode("openid")
            + "&state="
            + encode("spike-auth-state")
            + "&code_challenge="
            + encode(codeChallenge(codeVerifier))
            + "&code_challenge_method=S256"
            + "&redirect_uri="
            + encode(REDIRECT_URI);

    HttpResponse<String> loginPage =
        http.send(
            HttpRequest.newBuilder(URI.create(authUri)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(loginPage.statusCode()).isEqualTo(200);

    Matcher formActionMatcher = FORM_ACTION.matcher(loginPage.body());
    assertThat(formActionMatcher.find()).as("Login form action must be present").isTrue();
    String formAction = formActionMatcher.group(1).replace("&amp;", "&");
    URI formUri = URI.create(authUri).resolve(formAction);

    String credentials =
        "username=" + encode(email) + "&password=" + encode(password) + "&credentialId=";

    HttpResponse<String> submitted =
        http.send(
            HttpRequest.newBuilder(formUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieHeader(loginPage))
                .POST(HttpRequest.BodyPublishers.ofString(credentials))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(submitted.statusCode()).isEqualTo(302);

    String location = submitted.headers().firstValue("Location").orElse("");
    assertThat(location).startsWith(REDIRECT_URI);

    Matcher codeMatcher = AUTHORIZATION_CODE.matcher(location);
    assertThat(codeMatcher.find()).as("Authorization code in redirect").isTrue();
    String code = codeMatcher.group(1);

    String tokenBody =
        "grant_type=authorization_code"
            + "&code="
            + encode(code)
            + "&code_verifier="
            + encode(codeVerifier)
            + "&redirect_uri="
            + encode(REDIRECT_URI);

    HttpResponse<String> tokenResponse =
        postClientToken(registration.getProviderDetails().getTokenUri(), tokenBody);

    assertThat(tokenResponse.statusCode()).isEqualTo(200);
    JsonNode tokenJson = JSON.readTree(tokenResponse.body());
    return tokenJson.path("refresh_token").asText();
  }

  private static void executeResetCredentialsFlow(HttpClient http, String email, String newPassword)
      throws Exception {
    String codeVerifier = randomCodeVerifier();
    String authUri =
        registration.getProviderDetails().getAuthorizationUri()
            + "?response_type=code"
            + "&client_id="
            + encode(CLIENT_ID)
            + "&scope="
            + encode("openid")
            + "&state="
            + encode("reset-flow-state")
            + "&code_challenge="
            + encode(codeChallenge(codeVerifier))
            + "&code_challenge_method=S256"
            + "&redirect_uri="
            + encode(REDIRECT_URI);

    HttpResponse<String> loginPage =
        http.send(
            HttpRequest.newBuilder(URI.create(authUri)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(loginPage.statusCode()).isEqualTo(200);

    String cookies = cookieHeader(loginPage);

    Matcher resetLinkMatcher = RESET_CREDENTIALS_LINK.matcher(loginPage.body());
    assertThat(resetLinkMatcher.find())
        .as("Login page must contain reset-credentials link")
        .isTrue();

    String resetHref = resetLinkMatcher.group(1).replace("&amp;", "&");
    URI resetUri = URI.create(authUri).resolve(resetHref);

    HttpResponse<String> resetPage =
        http.send(
            HttpRequest.newBuilder(resetUri).header("Cookie", cookies).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(resetPage.statusCode()).isEqualTo(200);
    cookies = mergeCookies(cookies, resetPage);

    Matcher formActionMatcher = FORM_ACTION.matcher(resetPage.body());
    assertThat(formActionMatcher.find())
        .as("Reset credentials page must contain form action")
        .isTrue();
    String formActionHref = formActionMatcher.group(1).replace("&amp;", "&");
    URI formActionUri = resetUri.resolve(formActionHref);

    String resetSubmitData = "username=" + encode(email);

    HttpResponse<String> resetSubmitted =
        http.send(
            HttpRequest.newBuilder(formActionUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookies)
                .POST(HttpRequest.BodyPublishers.ofString(resetSubmitData))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(resetSubmitted.statusCode()).isIn(200, 302);
    cookies = mergeCookies(cookies, resetSubmitted);

    // Aguarda e-mail de redefinição no Mailpit
    String mailpitHost = "localhost".equals(MAILPIT.getHost()) ? "127.0.0.1" : MAILPIT.getHost();
    String mailpitLatestUrl =
        "http://" + mailpitHost + ":" + MAILPIT.getMappedPort(8025) + "/view/latest.html";

    String emailBody = null;
    for (int attempt = 0; attempt < 30; attempt++) {
      HttpResponse<String> mailResponse =
          http.send(
              HttpRequest.newBuilder(URI.create(mailpitLatestUrl)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (mailResponse.statusCode() == 200
          && mailResponse.body() != null
          && !mailResponse.body().isBlank()) {
        emailBody = mailResponse.body();
        break;
      }
      Thread.sleep(500);
    }

    assertThat(emailBody).as("Reset password email must be received in Mailpit").isNotBlank();

    String actionLink = extractActionLink(emailBody);
    URI rawActionUri = URI.create(actionLink);
    String actionPath = rawActionUri.getRawPath();
    String actionQuery = rawActionUri.getRawQuery();

    assertThat(actionPath).isEqualTo("/realms/" + REALM + "/login-actions/action-token");
    assertThat(actionQuery).contains("key=");

    URI expectedBaseUri = URI.create(issuerBaseUrl());
    String expectedScheme = expectedBaseUri.getScheme();
    String expectedAuthority = expectedBaseUri.getAuthority();
    URI actionTargetUri = expectedBaseUri.resolve(actionPath + "?" + actionQuery);

    // Navega até o formulário UPDATE_PASSWORD
    URI currentFlowUri = actionTargetUri;
    String actionFlowCookies = "";
    HttpResponse<String> flowPage = null;

    for (int i = 0; i < 10; i++) {
      flowPage =
          http.send(
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
          throw new IllegalStateException(
              "Redirect location diverted from expected Keycloak target scheme or authority");
        }
        currentFlowUri = resolvedLoc;
        continue;
      }

      if (statusCode == 200) {
        String body = flowPage.body() != null ? flowPage.body() : "";
        if (body.contains("password-new")
            || body.contains("name=\"password-confirm\"")
            || body.contains("name=\"password\"")) {
          break;
        }
        Matcher actionHrefMatcher = ACTION_TOKEN_HREF.matcher(body);
        if (actionHrefMatcher.find()) {
          String nextHref = actionHrefMatcher.group(1).replace("&amp;", "&");
          URI resolvedHref = currentFlowUri.resolve(nextHref);
          currentFlowUri = resolvedHref;
          continue;
        }
        Matcher formMatcher = FORM_ACTION.matcher(body);
        if (formMatcher.find()) {
          String nextAction = formMatcher.group(1).replace("&amp;", "&");
          URI formResolved = currentFlowUri.resolve(nextAction);
          currentFlowUri = formResolved;
          flowPage =
              http.send(
                  HttpRequest.newBuilder(currentFlowUri)
                      .header("Content-Type", "application/x-www-form-urlencoded")
                      .header("Cookie", actionFlowCookies)
                      .POST(HttpRequest.BodyPublishers.noBody())
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          actionFlowCookies = mergeCookies(actionFlowCookies, flowPage);
          if (flowPage.statusCode() >= 300 && flowPage.statusCode() < 400) {
            String loc = flowPage.headers().firstValue("Location").orElse("");
            if (!loc.isBlank()) {
              currentFlowUri = currentFlowUri.resolve(loc);
              continue;
            }
          }
        }
        break;
      }
      break;
    }

    assertThat(flowPage).isNotNull();
    assertThat(flowPage.statusCode()).isEqualTo(200);

    String flowBody = flowPage.body() != null ? flowPage.body() : "";
    assertThat(flowBody).contains("password-new");

    Matcher updatePwdActionMatcher = FORM_ACTION.matcher(flowBody);
    assertThat(updatePwdActionMatcher.find())
        .as("Update password page must contain form action")
        .isTrue();
    String updatePwdFormAction = updatePwdActionMatcher.group(1).replace("&amp;", "&");
    URI updatePwdFormUri = currentFlowUri.resolve(updatePwdFormAction);
    if (!expectedScheme.equalsIgnoreCase(updatePwdFormUri.getScheme())
        || !expectedAuthority.equalsIgnoreCase(updatePwdFormUri.getAuthority())) {
      throw new IllegalStateException(
          "Update password form action diverted from expected Keycloak target scheme or authority");
    }

    String updatePwdData =
        "password-new=" + encode(newPassword) + "&password-confirm=" + encode(newPassword);

    HttpResponse<String> pwdSubmitResponse =
        http.send(
            HttpRequest.newBuilder(updatePwdFormUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", actionFlowCookies)
                .POST(HttpRequest.BodyPublishers.ofString(updatePwdData))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(pwdSubmitResponse.statusCode()).isIn(200, 302, 303);
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

  private static String extractFormParam(String formData, String paramName) {
    if (formData == null || formData.isBlank()) {
      return "";
    }
    for (String pair : formData.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
        if (paramName.equals(key)) {
          return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
        }
      }
    }
    return "";
  }

  private static void awaitBackchannelRequests(int expectedCount, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (RECEIVED_BACKCHANNEL_REQUESTS.size() >= expectedCount) {
        return;
      }
      Thread.sleep(100);
    }
  }

  private static void assertNoBackchannelRequests(Duration observationWindow)
      throws InterruptedException {
    long deadline = System.nanoTime() + observationWindow.toNanos();
    while (System.nanoTime() < deadline) {
      assertThat(RECEIVED_BACKCHANNEL_REQUESTS)
          .as(
              "Back-channel logout requests received during password reset (Q2 measurement: 0 requests)")
          .isEmpty();
      Thread.sleep(100);
    }
    assertThat(RECEIVED_BACKCHANNEL_REQUESTS)
        .as(
            "Back-channel logout requests received during password reset (Q2 measurement: 0 requests)")
        .isEmpty();
  }

  private static HttpResponse<String> refresh(String refreshToken) throws Exception {
    return postClientToken(
        registration.getProviderDetails().getTokenUri(),
        "grant_type=refresh_token&refresh_token=" + encode(refreshToken));
  }

  private static HttpResponse<String> postClientToken(String uri, String body) throws Exception {
    String basic =
        Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  private static String fetchAdminToken(HttpClient http) throws Exception {
    String tokenEndpoint = issuerBaseUrl() + "/realms/master/protocol/openid-connect/token";
    String body =
        "grant_type=password"
            + "&client_id=admin-cli"
            + "&username=admin"
            + "&password=admin-test-only";

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body()).path("access_token").asText();
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

    throw new IllegalStateException("Realm import does not declare matching entry for " + field);
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
    byte[] digest =
        MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));

    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
