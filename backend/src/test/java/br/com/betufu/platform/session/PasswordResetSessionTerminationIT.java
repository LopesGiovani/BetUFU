package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.api.ProblemDetailsHandler;
import br.com.betufu.platform.security.OidcRevocationClient;
import br.com.betufu.platform.security.SecurityConfiguration;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventEndpoint;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventInboxEntity;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventInboxRepository;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventInboxService;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventProperties;
import br.com.betufu.platform.session.passwordreset.PasswordResetEventSecurityConfiguration;
import br.com.betufu.platform.session.passwordreset.PasswordResetSignedEnvelopeVerifier;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves the full D-40 password reset session termination chain and Bearer cryptographic validation
 * against a real Keycloak 26.7.1, PostgreSQL 18, and Redis 8.10.
 *
 * <p>Proves:
 *
 * <ul>
 *   <li>Evidence Gate 1: Real dispatcher and endpoint delivery terminating target sessions with
 *       SECURITY reason while control session survives;
 *   <li>Evidence Gate 2: Real cryptographic Bearer validation against Keycloak (forged, expired,
 *       wrong issuer, wrong audience, homonym realm role vs real realm-management client role);
 *   <li>D-40 E2E: Hosted and administrative password resets terminate all target sessions;
 *   <li>Key rotation via two executions of the origin (v1 previous, v2 current, v3 rejected);
 *   <li>Crash/restart recovery and healthy path completion (RTO &lt;= 60 s).
 * </ul>
 */
@SpringBootTest(
    classes = {
      SessionSecurityConfiguration.class,
      IdentitySessionTerminationService.class,
      SessionInvalidationListener.class,
      OidcSessionRevocationService.class,
      PasswordResetSignedEnvelopeVerifier.class,
      PasswordResetEventInboxService.class,
      PasswordResetEventEndpoint.class,
      PasswordResetEventSecurityConfiguration.class,
      SecurityConfiguration.class,
      ProblemDetailsHandler.class,
      PasswordResetSessionTerminationIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-termination-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "betufu.session.password-reset.current-version=v2",
      "betufu.session.password-reset.previous-version=v1",
      "betufu.session.password-reset.keys.v1=MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "betufu.session.password-reset.keys.v2=MjIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjA="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(PasswordResetSessionTerminationIT.TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class PasswordResetSessionTerminationIT {

  private static final String APPROVED_ACTIVITY_ATTRIBUTE =
      "br.com.betufu.platform.session.SelectiveTouchSessionRepository.approvedActivity";
  private static final String LAST_VALID_EPOCH_SECOND_ATTRIBUTE =
      "betufu.session.lastValidActivityAt.epochSecond";
  private static final String LAST_VALID_NANO_ATTRIBUTE = "betufu.session.lastValidActivityAt.nano";

  private static final String TARGET_SUBJECT_A = "termination-target-user-alpha";
  private static final String CONTROL_SUBJECT_B = "termination-control-user-beta";
  private static final String TEST_REALM = "betufu-local";

  private static final String KEY_ID_V1 = "v1";
  private static final String KEY_ID_V2 = "v2";
  private static final String KEY_ID_V3 = "v3";

  private static final String RAW_KEY_V1_BASE64 = "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final String RAW_KEY_V2_BASE64 = "MjIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjA=";
  private static final String RAW_KEY_V3_BASE64 = "MzIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjE=";

  private static final byte[] RAW_KEY_V1 = Base64.getDecoder().decode(RAW_KEY_V1_BASE64);
  private static final byte[] RAW_KEY_V2 = Base64.getDecoder().decode(RAW_KEY_V2_BASE64);
  private static final byte[] RAW_KEY_V3 = Base64.getDecoder().decode(RAW_KEY_V3_BASE64);

  private static final String MAILPIT_IMAGE_REFERENCE =
      "docker.io/axllent/mailpit:v1.30.7@sha256:"
          + "d5ecbb067db3705fa953d79e1b7f81ef84038df67aba6c52825d8c02a1ea748a";
  private static final String CANDIDATE_KEYCLOAK_IMAGE_REFERENCE =
      "betufu/keycloak:26.7.1-password-listener";
  private static final String OFFICIAL_KEYCLOAK_IMAGE_REFERENCE =
      "quay.io/keycloak/keycloak:26.7.1@sha256:"
          + "f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6";
  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final String KEYCLOAK_DB_NAME = "keycloak";
  private static final String KEYCLOAK_DB_USER = "keycloak";
  private static final String KEYCLOAK_DB_PASSWORD = "keycloak-test-only";
  private static final String KEYCLOAK_DB_ALIAS = "keycloak-postgres";
  private static final String TLS_STORE_PASSWORD = "betufu-e2e-store-only";
  private static final Duration REAL_CHAIN_TIMEOUT = Duration.ofSeconds(60);
  private static final String CLIENT_ID = "betufu-bff";
  private static final String REDIRECT_URI = "http://localhost:4200/login/oauth2/code/keycloak";

  private static final Path REALM_FILE = Path.of("..", "infra", "keycloak", "realm-betufu.json");
  private static final Path THEME_DIR = Path.of("..", "infra", "keycloak", "themes", "betufu");

  private static final Pattern FORM_ACTION =
      Pattern.compile("<form[^>]*\\saction=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
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

  private static Path fixtureDirectory;
  private static Path tlsKeyStore;
  private static Path tlsTrustStore;
  private static Path tlsCertificate;
  private static Path candidateRealmFile;

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withImagePullPolicy(imageName -> false)
          .withInitScript("postgres-audit-runtime-role.sql");

  @Container
  static final PostgreSQLContainer KEYCLOAK_POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName(KEYCLOAK_DB_NAME)
          .withUsername(KEYCLOAK_DB_USER)
          .withPassword(KEYCLOAK_DB_PASSWORD)
          .withNetwork(NETWORK)
          .withNetworkAliases(KEYCLOAK_DB_ALIAS)
          .withImagePullPolicy(imageName -> false);

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @Container
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse(MAILPIT_IMAGE_REFERENCE))
          .withNetwork(NETWORK)
          .withNetworkAliases("mailpit")
          .withImagePullPolicy(imageName -> false)
          .withExposedPorts(1025, 8025)
          .waitingFor(
              Wait.forHttp("/api/v1/messages")
                  .forPort(8025)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  @Container static final GenericContainer<?> OFFICIAL_KEYCLOAK = createOfficialKeycloakContainer();

  private static GenericContainer<?> createOfficialKeycloakContainer() {
    return new GenericContainer<>(DockerImageName.parse(OFFICIAL_KEYCLOAK_IMAGE_REFERENCE))
        .withNetwork(NETWORK)
        .withImagePullPolicy(imageName -> false)
        .withCopyFileToContainer(
            MountableFile.forHostPath(REALM_FILE),
            "/opt/keycloak/data/import/betufu-local-realm.json")
        .withCopyFileToContainer(
            MountableFile.forHostPath(THEME_DIR), "/opt/keycloak/themes/betufu")
        .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
        .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only")
        .withEnv("BETUFU_OIDC_CLIENT_SECRET", "test-termination-client-secret")
        .withEnv("BETUFU_TECHNICAL_PASSWORD", "technical-test-password-123")
        .withCommand("start-dev", "--import-realm")
        .withExposedPorts(8080)
        .waitingFor(
            Wait.forHttp("/realms/" + TEST_REALM + "/.well-known/openid-configuration")
                .forPort(8080)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
  }

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    ensureTlsFixture();
    if (!REDIS.isRunning()) {
      REDIS.start();
    }
    RedisContainerSupport.registerProperties(registry);
    registry.add("server.address", () -> "0.0.0.0");
    registry.add("server.ssl.enabled", () -> "true");
    registry.add("server.ssl.key-store", () -> tlsKeyStore.toUri().toString());
    registry.add("server.ssl.key-store-password", () -> TLS_STORE_PASSWORD);
    registry.add("server.ssl.key-password", () -> TLS_STORE_PASSWORD);
    registry.add("server.ssl.key-store-type", () -> "PKCS12");
    registry.add("server.ssl.key-alias", () -> "betufu-wave9-it");
  }

  @LocalServerPort private int serverPort;

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MutableTestClock clock;

  @Autowired private PasswordResetEventInboxRepository inboxRepository;

  @Autowired private PasswordResetEventInboxService inboxService;

  @Autowired private IdentitySessionTerminationService sessionTerminationService;

  @Autowired private OAuth2AuthorizedClientService authorizedClientService;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired private PasswordResetSignedEnvelopeVerifier verifier;

  @Autowired private PasswordResetEventProperties properties;

  @Autowired private PasswordResetEventEndpoint endpoint;

  @Autowired private OidcRevocationClient oidcRevocationClient;

  private SelectiveTouchSessionRepository sessionRepository;
  private MockMvc mockMvc;

  private static String keycloakBaseUrl;
  private GenericContainer<?> candidateKeycloak;
  private String activeCandidateKeyId;
  private String activeCandidateKey;
  private boolean activeCandidateDispatcherEnabled;
  private int activeCandidateEndpointPort = -1;

  @BeforeAll
  void startRealCandidateAfterSpringHttpsPortIsKnown() throws Exception {
    org.testcontainers.Testcontainers.exposeHostPorts(serverPort);
    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);
  }

  @AfterAll
  void stopCandidateAndDeleteEphemeralTlsMaterial() throws IOException {
    stopCandidate();
    deleteFixtureFile(candidateRealmFile);
    deleteFixtureFile(tlsCertificate);
    deleteFixtureFile(tlsTrustStore);
    deleteFixtureFile(tlsKeyStore);
    deleteFixtureFile(fixtureDirectory);
  }

  @BeforeEach
  void setup() throws Exception {
    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
              return null;
            });
    jdbcTemplate.execute("TRUNCATE TABLE public.password_reset_event_inbox");
    truncateCandidateOutbox();
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    reset(oidcRevocationClient);
    sessionRepository =
        (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    restoreCanonicalProperties();
  }

  @AfterEach
  void cleanup() throws Exception {
    restoreCanonicalProperties();
    if (candidateKeycloak != null
        && (!KEY_ID_V2.equals(activeCandidateKeyId)
            || !RAW_KEY_V2_BASE64.equals(activeCandidateKey)
            || !activeCandidateDispatcherEnabled
            || activeCandidateEndpointPort != serverPort)) {
      stopCandidate();
      truncateCandidateOutbox();
      ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);
    }
  }

  private void restoreCanonicalProperties() {
    properties.setCurrentVersion(KEY_ID_V2);
    properties.setPreviousVersion(KEY_ID_V1);
    properties.setKeys(
        Map.of(
            KEY_ID_V1, RAW_KEY_V1_BASE64,
            KEY_ID_V2, RAW_KEY_V2_BASE64));
  }

  @Test
  @Order(1)
  @DisplayName(
      "Task 02-29-03: variáveis BETUFU ligam o application.yaml às propriedades de password reset")
  void passwordResetEnvironmentVariables_bindThroughApplicationYaml() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PasswordResetBindingConfiguration.class)
        .withPropertyValues(
            "BETUFU_PASSWORD_RESET_CURRENT_VERSION=" + KEY_ID_V2,
            "BETUFU_PASSWORD_RESET_PREVIOUS_VERSION=" + KEY_ID_V1,
            "BETUFU_PASSWORD_RESET_KEY_V1=" + RAW_KEY_V1_BASE64,
            "BETUFU_PASSWORD_RESET_KEY_V2=" + RAW_KEY_V2_BASE64)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PasswordResetEventProperties.class);
              PasswordResetEventProperties bound =
                  context.getBean(PasswordResetEventProperties.class);
              assertThat(bound.getCurrentVersion()).isEqualTo(KEY_ID_V2);
              assertThat(bound.getPreviousVersion()).isEqualTo(KEY_ID_V1);
              assertThat(bound.resolveKey(KEY_ID_V1)).containsExactly(RAW_KEY_V1);
              assertThat(bound.resolveKey(KEY_ID_V2)).containsExactly(RAW_KEY_V2);
            });
  }

  @Test
  @Order(2)
  @DisplayName("Task 02-29-03: versão corrente sem chave é recusada sem efeito")
  void allowedCurrentVersionWithoutConfiguredKey_isRejectedWithoutEffect() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session);
    properties.setCurrentVersion(KEY_ID_V2);
    properties.setPreviousVersion(KEY_ID_V1);
    properties.setKeys(Map.of(KEY_ID_V1, RAW_KEY_V1_BASE64));

    String sourceEventId = "evt-missing-current-key-" + UUID.randomUUID();
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());
    byte[] bodyBytes =
        ("""
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """)
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, timestampHeader)
            .getBytes(StandardCharsets.UTF_8);
    String signature =
        verifier.sign(
            RAW_KEY_V2,
            KEY_ID_V2,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            verifier.computeBodySha256(bodyBytes));

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID_V2)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    assertThat(inboxRepository.findBySourceEventId(sourceEventId)).isEmpty();
    assertThat(sessionRepository.findById(session.getId())).isNotNull();
  }

  @Test
  @Order(3)
  @DisplayName(
      "Task 02-29-03: reconciliador atualiza realm persistido, preserva configuração e é idempotente")
  void realmListenerReconciler_updatesPersistedRealmAndIsIdempotent() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);

    ObjectNode legacyConfiguration = (ObjectNode) fetchRealmEventConfiguration(http, adminToken);
    legacyConfiguration.putArray("eventsListeners").add("jboss-logging");
    updateRealmEventConfiguration(http, adminToken, legacyConfiguration);

    JsonNode before = fetchRealmEventConfiguration(http, adminToken);
    assertThat(before.path("eventsListeners").toString())
        .contains("jboss-logging")
        .doesNotContain("betufu-password-event-listener");

    ReconcilerExecution first = runRealmListenerReconciler();
    assertThat(first.exitCode()).as(first.output()).isZero();
    assertThat(first.output())
        .contains("APPLIED")
        .doesNotContain("admin-test-only")
        .doesNotContain("access_token");

    adminToken = fetchMasterAdminToken(http);
    JsonNode afterFirst = fetchRealmEventConfiguration(http, adminToken);
    assertThat(afterFirst.path("eventsListeners").toString())
        .contains("jboss-logging", "betufu-password-event-listener");
    assertNonListenerEventConfigurationPreserved(before, afterFirst);

    ReconcilerExecution second = runRealmListenerReconciler();
    assertThat(second.exitCode()).as(second.output()).isZero();
    assertThat(second.output())
        .contains("UNCHANGED")
        .doesNotContain("admin-test-only")
        .doesNotContain("access_token");

    adminToken = fetchMasterAdminToken(http);
    JsonNode afterSecond = fetchRealmEventConfiguration(http, adminToken);
    assertThat(afterSecond).isEqualTo(afterFirst);

    stopCandidate();
    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);

    http = HttpClient.newHttpClient();
    adminToken = fetchMasterAdminToken(http);
    JsonNode afterRestart = fetchRealmEventConfiguration(http, adminToken);
    assertThat(afterRestart.path("eventsListeners").toString())
        .contains("jboss-logging", "betufu-password-event-listener");
    assertNonListenerEventConfigurationPreserved(afterFirst, afterRestart);
  }

  private static synchronized void ensureTlsFixture() {
    if (tlsKeyStore != null) {
      return;
    }
    try {
      fixtureDirectory = Files.createTempDirectory("betufu-wave9-e2e-");
      tlsKeyStore = fixtureDirectory.resolve("backend-https.p12");
      tlsTrustStore = fixtureDirectory.resolve("candidate-trust.p12");
      tlsCertificate = fixtureDirectory.resolve("backend-https.cer");
      candidateRealmFile = fixtureDirectory.resolve("realm-betufu-with-password-listener.json");

      runKeytool(
          "-genkeypair",
          "-alias",
          "betufu-wave9-it",
          "-keyalg",
          "RSA",
          "-keysize",
          "2048",
          "-validity",
          "2",
          "-storetype",
          "PKCS12",
          "-keystore",
          tlsKeyStore.toString(),
          "-storepass",
          TLS_STORE_PASSWORD,
          "-keypass",
          TLS_STORE_PASSWORD,
          "-dname",
          "CN=host.testcontainers.internal,OU=Wave9,O=BETUFU,L=Uberlandia,ST=MG,C=BR",
          "-ext",
          "SAN=dns:host.testcontainers.internal,dns:localhost,ip:127.0.0.1");
      runKeytool(
          "-exportcert",
          "-alias",
          "betufu-wave9-it",
          "-keystore",
          tlsKeyStore.toString(),
          "-storepass",
          TLS_STORE_PASSWORD,
          "-file",
          tlsCertificate.toString());
      runKeytool(
          "-importcert",
          "-noprompt",
          "-alias",
          "betufu-wave9-it",
          "-storetype",
          "PKCS12",
          "-keystore",
          tlsTrustStore.toString(),
          "-storepass",
          TLS_STORE_PASSWORD,
          "-file",
          tlsCertificate.toString());

      String realm = Files.readString(REALM_FILE, StandardCharsets.UTF_8);
      JsonNode realmDocument = JSON.readTree(realm);
      assertThat(realmDocument.path("eventsListeners").toString())
          .isEqualTo("[\"jboss-logging\",\"betufu-password-event-listener\"]");
      Files.writeString(candidateRealmFile, realm, StandardCharsets.UTF_8);
    } catch (Exception failure) {
      throw new IllegalStateException("Could not create the ephemeral Wave 9 TLS fixture", failure);
    }
  }

  private static void runKeytool(String... arguments) throws Exception {
    String executable =
        System.getProperty("os.name", "").toLowerCase().contains("win")
            ? Path.of(System.getProperty("java.home"), "bin", "keytool.exe").toString()
            : Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    List<String> command = new java.util.ArrayList<>();
    command.add(executable);
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("keytool exited with " + exitCode + ": " + output);
    }
  }

  private static HttpClient trustedBackendHttpClient() throws Exception {
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    try (java.io.InputStream input = Files.newInputStream(tlsTrustStore)) {
      trustStore.load(input, TLS_STORE_PASSWORD.toCharArray());
    }
    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init(trustStore);
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustManagers.getTrustManagers(), new SecureRandom());
    return HttpClient.newBuilder().sslContext(sslContext).build();
  }

  private void ensureCandidate(
      String keyId, String keyBase64, boolean dispatcherEnabled, int endpointPort)
      throws Exception {
    if (candidateKeycloak != null
        && candidateKeycloak.isRunning()
        && Objects.equals(activeCandidateKeyId, keyId)
        && Objects.equals(activeCandidateKey, keyBase64)
        && activeCandidateDispatcherEnabled == dispatcherEnabled
        && activeCandidateEndpointPort == endpointPort) {
      return;
    }

    stopCandidate();
    String endpoint =
        "https://host.testcontainers.internal:"
            + endpointPort
            + PasswordResetEventEndpoint.ENDPOINT_PATH;

    GenericContainer<?> candidate =
        new GenericContainer<>(DockerImageName.parse(CANDIDATE_KEYCLOAK_IMAGE_REFERENCE))
            .withNetwork(NETWORK)
            .withAccessToHost(true)
            .withImagePullPolicy(imageName -> false)
            .withCopyFileToContainer(
                MountableFile.forHostPath(candidateRealmFile),
                "/opt/keycloak/data/import/betufu-local-realm.json")
            .withCopyFileToContainer(
                MountableFile.forHostPath(THEME_DIR), "/opt/keycloak/themes/betufu")
            .withCopyFileToContainer(
                MountableFile.forHostPath(tlsTrustStore),
                "/opt/keycloak/conf/betufu-wave9-trust.p12")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only")
            .withEnv("KC_DB", "postgres")
            .withEnv(
                "KC_DB_URL", "jdbc:postgresql://" + KEYCLOAK_DB_ALIAS + ":5432/" + KEYCLOAK_DB_NAME)
            .withEnv("KC_DB_USERNAME", KEYCLOAK_DB_USER)
            .withEnv("KC_DB_PASSWORD", KEYCLOAK_DB_PASSWORD)
            .withEnv("BETUFU_OIDC_CLIENT_SECRET", "test-termination-client-secret")
            .withEnv("BETUFU_TECHNICAL_PASSWORD", "technical-test-password-123")
            .withEnv(
                "JAVA_OPTS_APPEND",
                "-Djavax.net.ssl.trustStore=/opt/keycloak/conf/betufu-wave9-trust.p12 "
                    + "-Djavax.net.ssl.trustStorePassword="
                    + TLS_STORE_PASSWORD
                    + " "
                    + "-Djavax.net.ssl.trustStoreType=PKCS12")
            .withCommand(
                "start-dev",
                "--import-realm",
                "--spi-events-listener--betufu-password-event-listener--enabled="
                    + dispatcherEnabled,
                "--spi-events-listener--betufu-password-event-listener--endpoint=" + endpoint,
                "--spi-events-listener--betufu-password-event-listener--hmac-key=" + keyBase64,
                "--spi-events-listener--betufu-password-event-listener--key-id=" + keyId)
            .withExposedPorts(8080)
            .dependsOn(KEYCLOAK_POSTGRESQL, MAILPIT)
            .waitingFor(
                Wait.forHttp("/realms/" + TEST_REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    try {
      candidate.start();
    } catch (RuntimeException startFailure) {
      candidate.stop();
      throw startFailure;
    }

    candidateKeycloak = candidate;
    activeCandidateKeyId = keyId;
    activeCandidateKey = keyBase64;
    activeCandidateDispatcherEnabled = dispatcherEnabled;
    activeCandidateEndpointPort = endpointPort;
    String host = "localhost".equals(candidate.getHost()) ? "127.0.0.1" : candidate.getHost();
    keycloakBaseUrl = "http://" + host + ":" + candidate.getMappedPort(8080);
    assertPasswordListenerEnabledInImportedRealm();
  }

  private void stopCandidate() {
    if (candidateKeycloak != null) {
      candidateKeycloak.stop();
      candidateKeycloak = null;
    }
    activeCandidateKeyId = null;
    activeCandidateKey = null;
    activeCandidateDispatcherEnabled = false;
    activeCandidateEndpointPort = -1;
  }

  private void assertPasswordListenerEnabledInImportedRealm() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    assertThat(fetchRealmEventConfiguration(http, adminToken).path("eventsListeners").toString())
        .contains("betufu-password-event-listener");
  }

  private static JsonNode fetchRealmEventConfiguration(HttpClient http, String adminToken)
      throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(keycloakBaseUrl + "/admin/realms/" + TEST_REALM + "/events/config"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("listener config status; body=%s", response.body())
        .isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private static void updateRealmEventConfiguration(
      HttpClient http, String adminToken, JsonNode configuration) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(keycloakBaseUrl + "/admin/realms/" + TEST_REALM + "/events/config"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(configuration)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("listener config update status; body=%s", response.body())
        .isIn(200, 204);
  }

  private static void assertNonListenerEventConfigurationPreserved(
      JsonNode before, JsonNode after) {
    assertThat(after.path("eventsEnabled")).isEqualTo(before.path("eventsEnabled"));
    assertThat(after.path("eventsExpiration")).isEqualTo(before.path("eventsExpiration"));
    assertThat(after.path("enabledEventTypes")).isEqualTo(before.path("enabledEventTypes"));
    assertThat(after.path("adminEventsEnabled")).isEqualTo(before.path("adminEventsEnabled"));
    assertThat(after.path("adminEventsDetailsEnabled"))
        .isEqualTo(before.path("adminEventsDetailsEnabled"));
  }

  private static ReconcilerExecution runRealmListenerReconciler() throws Exception {
    Path script =
        Path.of("..", "scripts", "reconcile-keycloak-event-listeners.mjs")
            .toAbsolutePath()
            .normalize();
    assertThat(script).isRegularFile();

    ProcessBuilder builder =
        new ProcessBuilder(
            "node",
            script.toString(),
            "--apply",
            "--base-url",
            keycloakBaseUrl,
            "--realm",
            TEST_REALM);
    builder.redirectErrorStream(true);
    builder.environment().put("KC_BOOTSTRAP_ADMIN_USERNAME", "admin");
    builder.environment().put("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only");

    Process process = builder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(finished).as("reconciler must finish within 60 seconds; output=%s", output).isTrue();
    return new ReconcilerExecution(process.exitValue(), output);
  }

  private void truncateCandidateOutbox() throws SQLException {
    try (Connection connection = openCandidateDatabase();
        java.sql.Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE public.betufu_password_reset_outbox");
    }
  }

  private static Connection openCandidateDatabase() throws SQLException {
    return DriverManager.getConnection(
        KEYCLOAK_POSTGRESQL.getJdbcUrl(), KEYCLOAK_DB_USER, KEYCLOAK_DB_PASSWORD);
  }

  private static void deleteFixtureFile(Path path) throws IOException {
    if (path != null) {
      Files.deleteIfExists(path);
    }
  }

  // =========================================================================
  // Gate 2: Validação Criptográfica Real do Bearer contra Keycloak Real
  // =========================================================================

  @Test
  @DisplayName(
      "Gate 2.1: Bearer com assinatura forjada é recusado com 401 Unauthorized pelo Keycloak real")
  void bearerWithForgedSignature_isRejectedWith401() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String validToken =
        issueAuthorizedRecoveryToken(
            http,
            adminToken,
            "forged-signature",
            "ForgedSignaturePassword123!",
            keycloakBaseUrl,
            null);
    assertSignedTokenFixture(validToken, keycloakBaseUrl + "/realms/" + TEST_REALM, true, true);
    String forgedToken = tamperJwtSignature(validToken);

    assertThat(forgedToken.split("\\.")[0]).isEqualTo(validToken.split("\\.")[0]);
    assertThat(forgedToken.split("\\.")[1]).isEqualTo(validToken.split("\\.")[1]);
    assertThat(forgedToken.split("\\.")[2]).isNotEqualTo(validToken.split("\\.")[2]);

    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + forgedToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Bearer com assinatura forjada deve ser recusado com 401")
        .isEqualTo(401);
    assertThat(response.headers().firstValue("WWW-Authenticate"))
        .isPresent()
        .hasValueSatisfying(h -> assertThat(h).contains("Bearer"));
  }

  @Test
  @DisplayName("Gate 2.2: Bearer expirado é recusado com 401 Unauthorized pelo Keycloak real")
  void expiredBearerToken_isRejectedWith401() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String expiredToken =
        issueAuthorizedRecoveryToken(
            http, adminToken, "expired-token", "ExpiredTokenPassword123!", keycloakBaseUrl, 1);
    JsonNode claims =
        assertSignedTokenFixture(
            expiredToken, keycloakBaseUrl + "/realms/" + TEST_REALM, true, false);
    long issuedAt = claims.path("iat").asLong();
    long expiresAt = claims.path("exp").asLong();
    assertThat(expiresAt - issuedAt)
        .as("a fixture precisa emitir um token realmente assinado e de vida curta")
        .isBetween(1L, 5L);
    awaitTokenExpiry(expiresAt);

    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + expiredToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Bearer expirado deve ser recusado com 401")
        .isEqualTo(401);
  }

  @Test
  @DisplayName(
      "Gate 2.3: Bearer com issuer incorreto é recusado com 401 Unauthorized pelo Keycloak real")
  void bearerWithIncorrectIssuer_isRejectedWith401() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String alternateBaseUrl = alternateLoopbackBaseUrl();
    String wrongIssuerToken =
        issueAuthorizedRecoveryToken(
            http, adminToken, "wrong-issuer", "WrongIssuerPassword123!", alternateBaseUrl, null);
    JsonNode claims =
        assertSignedTokenFixture(
            wrongIssuerToken, alternateBaseUrl + "/realms/" + TEST_REALM, true, true);
    assertThat(claims.path("iss").asText())
        .as("issuer deve ser a unica precondicao divergente")
        .isNotEqualTo(keycloakBaseUrl + "/realms/" + TEST_REALM);

    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + wrongIssuerToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "Bearer assinado pela chave correta, mas emitido para outro issuer, deve ser recusado com 401")
        .isEqualTo(401);
  }

  @Test
  @DisplayName(
      "Gate 2.4: Bearer com audience incorreta é recusado com 401 Unauthorized pelo Keycloak real")
  void bearerWithIncorrectAudience_isRejectedWith401() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String username = "wrong-audience-" + UUID.randomUUID().toString().substring(0, 8);
    String password = "WrongAudiencePassword123!";
    createUserInRealm(http, adminToken, TEST_REALM, username, password);
    String clientId = "wrong-audience-client-" + UUID.randomUUID().toString().substring(0, 8);
    ensureDirectAccessClientExists(http, adminToken, TEST_REALM, clientId, false, null);
    String invalidAudienceToken =
        fetchUserTokenDirect(http, TEST_REALM, clientId, username + "@betufu.local", password);
    assertSignedTokenFixture(
        invalidAudienceToken, keycloakBaseUrl + "/realms/" + TEST_REALM, false, true);

    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + invalidAudienceToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Bearer com audience incorreta deve ser recusado com 401")
        .isEqualTo(401);
  }

  @Test
  @DisplayName(
      "Gate 2.5: Papel de realm homônimo manage-realm NÃO concede autoridade (retorna 403)")
  void realmRoleHomonymManageRealm_doesNotGrantAuthority_returns403() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);

    // 1. Cria usuário dedicado no realm betufu-local
    String username = "homonym-user-" + UUID.randomUUID().toString().substring(0, 8);
    String userId =
        createUserInRealm(http, adminToken, TEST_REALM, username, "HomonymPassword123!");

    // 2. Cria role de realm homônima chamada manage-realm (se ainda não existir)
    ensureRealmRoleExists(http, adminToken, TEST_REALM, "manage-realm");

    // 3. Atribui a role de realm homônima ao usuário
    assignRealmRoleToUser(http, adminToken, TEST_REALM, userId, "manage-realm");

    // 4. A audience e adicionada sem conceder o papel de cliente. Assim o 403 prova
    // especificamente a guarda de papel, e nao uma recusa anterior por audience.
    String clientId = "homonym-client-" + UUID.randomUUID().toString().substring(0, 8);
    ensureDirectAccessClientExists(http, adminToken, TEST_REALM, clientId, true, null);
    String userToken =
        fetchUserTokenDirect(
            http, TEST_REALM, clientId, username + "@betufu.local", "HomonymPassword123!");
    JsonNode claims =
        assertSignedTokenFixture(userToken, keycloakBaseUrl + "/realms/" + TEST_REALM, true, true);
    assertThat(tokenHasRealmRole(claims, "manage-realm")).isTrue();
    assertThat(tokenHasClientRole(claims, "realm-management", "manage-realm")).isFalse();

    // 5. Executa chamada ao recovery endpoint
    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + userToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "Usuário com apenas a role de realm homônima manage-realm deve ser recusado com 403 Forbidden")
        .isEqualTo(403);
  }

  @Test
  @DisplayName(
      "Gate 2.6: Somente o papel real do cliente realm-management chamado manage-realm é aceito (retorna 200)")
  void clientRoleRealmManagementManageRealm_isAccepted_returns200() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);

    // 1. Cria usuário dedicado no realm betufu-local
    String username = "admin-mgmt-user-" + UUID.randomUUID().toString().substring(0, 8);
    String userId =
        createUserInRealm(http, adminToken, TEST_REALM, username, "RealAdminPassword123!");

    // 2. Atribui a role de cliente realm-management/manage-realm ao usuário
    assignClientRoleToUser(
        http, adminToken, TEST_REALM, userId, "realm-management", "manage-realm");

    // 3. Obtém token do usuário
    String clientId = "real-management-client-" + UUID.randomUUID().toString().substring(0, 8);
    ensureDirectAccessClientExists(http, adminToken, TEST_REALM, clientId, false, null);
    String userToken =
        fetchUserTokenDirect(
            http, TEST_REALM, clientId, username + "@betufu.local", "RealAdminPassword123!");
    JsonNode claims =
        assertSignedTokenFixture(userToken, keycloakBaseUrl + "/realms/" + TEST_REALM, true, true);
    assertThat(tokenHasClientRole(claims, "realm-management", "manage-realm")).isTrue();

    // 4. Executa chamada ao recovery endpoint
    String recoveryUrl =
        keycloakBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + userToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Usuário com o papel real realm-management/manage-realm deve ser aceito com 200 OK")
        .isEqualTo(200);
  }

  @Test
  @DisplayName(
      "Gate 2 Controle: Imagem oficial sem o provider retorna 404 para endpoint de recuperação, provando discriminação contra falso positivo")
  void officialImageWithoutProvider_returns404_provingDiscrimination() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String host =
        "localhost".equals(OFFICIAL_KEYCLOAK.getHost()) ? "127.0.0.1" : OFFICIAL_KEYCLOAK.getHost();
    String officialBaseUrl = "http://" + host + ":" + OFFICIAL_KEYCLOAK.getMappedPort(8080);

    // Obtém token de admin master válido na imagem oficial
    String tokenEndpoint = officialBaseUrl + "/realms/master/protocol/openid-connect/token";
    String body =
        "grant_type=password"
            + "&client_id=admin-cli"
            + "&username=admin"
            + "&password=admin-test-only";

    HttpResponse<String> tokenResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(tokenResponse.statusCode()).isEqualTo(200);
    String officialAdminToken = JSON.readTree(tokenResponse.body()).path("access_token").asText();

    // Tenta acessar o endpoint de recuperação na imagem oficial com admin token válido
    String recoveryUrl =
        officialBaseUrl + "/realms/" + TEST_REALM + "/password-reset-recovery/failed";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(recoveryUrl))
                .header("Authorization", "Bearer " + officialAdminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    // Prova que na imagem oficial sem o provider o endpoint não existe (404),
    // discriminando que as respostas 200/401/403 nos gates 2.1-2.6 decorrem exclusivamente
    // do provider instalado na imagem candidata e não por ausência de código
    assertThat(response.statusCode())
        .as(
            "Na imagem oficial sem o provider, /password-reset-recovery/failed deve retornar 404 Not Found")
        .isEqualTo(404);
  }

  // =========================================================================
  // Gate 1 & D-40: Cadeia Real de Dispatcher e Endpoint & Encerramento de Sessões
  // =========================================================================

  @Test
  @Order(20)
  @DisplayName("Gate 1 real: candidata entrega por HTTPS e encerra duas sessões do alvo")
  void candidateDispatcherAndRealHttpsEndpoint_terminateTargetSessions() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String alias = "real-chain-" + UUID.randomUUID().toString().substring(0, 8);
    String subject =
        createUserInRealm(http, adminToken, TEST_REALM, alias, "RealChainPassword123!");
    LocalSessionFixture sessions = createTwoTargetSessionsAndControl(subject);

    triggerAdministrativePasswordReset(http, adminToken, subject, "RealChainPassword456!");

    RealDelivery delivery = awaitRealDelivery(subject, REAL_CHAIN_TIMEOUT);
    assertThat(delivery.origin().eventType()).isEqualTo("ADMIN_RESET_PASSWORD");
    assertCompleteRealDelivery(delivery, subject, sessions, 1);

    requeueOriginOutbox(delivery.sourceEventId());
    awaitOriginOutbox(
        subject,
        row ->
            delivery.sourceEventId().equals(row.sourceEventId())
                && "DELIVERED".equals(row.status()),
        REAL_CHAIN_TIMEOUT);

    assertThat(inboxRepository.findBySourceEventId(delivery.sourceEventId()))
        .get()
        .extracting(PasswordResetEventInboxEntity::getStatus)
        .isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(sessionRepository.findByPrincipalName(subject)).isEmpty();
    assertThat(sessionRepository.findById(sessions.control().getId())).isNotNull();
    verify(oidcRevocationClient, times(1))
        .revokeRefreshToken(any(ClientRegistration.class), eq("test-refresh-token"));
  }

  @Test
  @DisplayName("Simulação contratual: envelope HMAC direto exerce idempotência do destino")
  void contractSimulation_directHmacEnvelopeExercisesDestinationIdempotency() throws Exception {
    // 1. Duas sessões ativas para Target A e uma sessão para Control B no Redis
    SelectiveTouchSessionRepository.SelectiveSession sessionA1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    SelectiveTouchSessionRepository.SelectiveSession sessionA2 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA1);
    sessionRepository.save(sessionA2);

    SelectiveTouchSessionRepository.SelectiveSession sessionB1 =
        createAuthenticatedSession(CONTROL_SUBJECT_B);
    sessionRepository.save(sessionB1);

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(2);
    assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);

    // 2. Monta envelope da outbox e assina com a chave corrente (v2)
    String sourceEventId = "evt-e2e-gate1-" + UUID.randomUUID();
    Instant now = clock.instant();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(
                sourceEventId, TARGET_SUBJECT_A, TEST_REALM, String.valueOf(now.toEpochMilli()));

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(now.toEpochMilli());
    String signature =
        verifier.sign(
            RAW_KEY_V2,
            KEY_ID_V2,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    // 3. Envia para o endpoint do backend
    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID_V2)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
        .andExpect(jsonPath("$.effectExecuted").value(true));

    // 4. As 2 sessões do alvo foram destruídas
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
    assertThat(sessionRepository.findById(sessionA1.getId())).isNull();
    assertThat(sessionRepository.findById(sessionA2.getId())).isNull();

    // 5. A sessão de controle permanece ativa no Redis
    assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);
    assertThat(sessionRepository.findById(sessionB1.getId())).isNotNull();

    // 6. Registro no PostgreSQL gravado como DELIVERED
    Optional<PasswordResetEventInboxEntity> inboxOpt =
        inboxRepository.findBySourceEventId(sourceEventId);
    assertThat(inboxOpt).isPresent();
    assertThat(inboxOpt.get().getStatus())
        .isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);

    // 7. Prova de entrega única: Criar nova sessão para o alvo e reprocessar mesmo sourceEventId
    SelectiveTouchSessionRepository.SelectiveSession sessionA3 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA3);
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID_V2)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.effectExecuted").value(false));

    // A sessão pós-reset sobrevive
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    assertThat(sessionRepository.findById(sessionA3.getId())).isNotNull();
  }

  @Test
  @DisplayName(
      "Gate 1: Credencial não assinada ou adulterada é recusada com 401 sem encerramento de sessão")
  void unsignedOrTamperedCredential_isRejectedWith401_withoutSessionTermination() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA);

    String sourceEventId = "evt-unsigned-" + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(
                sourceEventId,
                TARGET_SUBJECT_A,
                TEST_REALM,
                String.valueOf(clock.instant().toEpochMilli()));

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    HttpClient https = trustedBackendHttpClient();
    URI endpointUri =
        URI.create("https://localhost:" + serverPort + PasswordResetEventEndpoint.ENDPOINT_PATH);

    HttpResponse<String> unsigned =
        https.send(
            HttpRequest.newBuilder(endpointUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(unsigned.statusCode()).as("unsigned HTTPS request").isEqualTo(401);

    HttpResponse<String> tampered =
        https.send(
            HttpRequest.newBuilder(endpointUri)
                .header("Content-Type", "application/json")
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID_V2)
                .header(
                    PasswordResetEventEndpoint.HEADER_TIMESTAMP,
                    String.valueOf(clock.instant().toEpochMilli()))
                .header(
                    PasswordResetEventEndpoint.HEADER_SIGNATURE,
                    "bad-sig-1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(tampered.statusCode()).as("tampered HTTPS request").isEqualTo(401);

    // Sessão permanece ativa e nenhum registro no banco
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    assertThat(inboxRepository.findBySourceEventId(sourceEventId)).isEmpty();
  }

  // =========================================================================
  // D-40: Reset Hospedado e Administrativo
  // =========================================================================

  @Test
  @Order(21)
  @DisplayName("D-40: Redefinição de senha hospedada encerra sessões do alvo e preserva controle")
  void hostedPasswordReset_terminatesTargetSessionsLeavingControlIntact() throws Exception {
    HttpClient adminHttp = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(adminHttp);
    String alias = "hosted-reset-" + UUID.randomUUID().toString().substring(0, 8);
    String email = alias + "@betufu.local";
    String subject =
        createUserInRealm(adminHttp, adminToken, TEST_REALM, alias, "HostedPassword123!");
    LocalSessionFixture sessions = createTwoTargetSessionsAndControl(subject);

    triggerHostedPasswordReset(email, "HostedPassword456!");

    RealDelivery delivery = awaitRealDelivery(subject, REAL_CHAIN_TIMEOUT);
    assertThat(delivery.origin().eventType()).isEqualTo("UPDATE_CREDENTIAL");
    assertCompleteRealDelivery(delivery, subject, sessions, 1);
  }

  // =========================================================================
  // Rotação de Chaves (current + previous) via Duas Execuções da Origem
  // =========================================================================

  @Test
  @Order(22)
  @DisplayName(
      "Rotação de chaves: Destino aceita chave anterior (v1) e chave corrente (v2) por duas execuções da origem, e recusa terceira (v3)")
  void keyRotation_acceptsBothCurrentAndPreviousVersionsViaTwoOriginExecutions() throws Exception {
    HttpClient http = HttpClient.newHttpClient();

    ensureCandidate(KEY_ID_V1, RAW_KEY_V1_BASE64, true, serverPort);
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    String adminTokenV1 = fetchMasterAdminToken(http);
    String subjectV1 =
        createUserInRealm(
            http,
            adminTokenV1,
            TEST_REALM,
            "rotation-v1-" + UUID.randomUUID().toString().substring(0, 8),
            "RotationV1Password123!");
    LocalSessionFixture sessionsV1 =
        createTwoTargetSessionsAndControl(subjectV1, "rotation-control-v1");
    triggerAdministrativePasswordReset(http, adminTokenV1, subjectV1, "RotationV1Password456!");
    RealDelivery deliveryV1 = awaitRealDelivery(subjectV1, REAL_CHAIN_TIMEOUT);
    assertThat(deliveryV1.origin().eventType()).isEqualTo("ADMIN_RESET_PASSWORD");
    assertCompleteRealDelivery(deliveryV1, subjectV1, sessionsV1, 1);

    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    properties.setCurrentVersion(KEY_ID_V2);
    properties.setPreviousVersion(null);
    properties.setKeys(Map.of(KEY_ID_V2, RAW_KEY_V2_BASE64));
    String adminTokenV2 = fetchMasterAdminToken(http);
    String subjectV2 =
        createUserInRealm(
            http,
            adminTokenV2,
            TEST_REALM,
            "rotation-v2-" + UUID.randomUUID().toString().substring(0, 8),
            "RotationV2Password123!");
    LocalSessionFixture sessionsV2 =
        createTwoTargetSessionsAndControl(subjectV2, "rotation-control-v2");
    triggerAdministrativePasswordReset(http, adminTokenV2, subjectV2, "RotationV2Password456!");
    RealDelivery deliveryV2 = awaitRealDelivery(subjectV2, REAL_CHAIN_TIMEOUT);
    assertThat(deliveryV2.origin().eventType()).isEqualTo("ADMIN_RESET_PASSWORD");
    assertCompleteRealDelivery(deliveryV2, subjectV2, sessionsV2, 2);

    restoreCanonicalProperties();
    ensureCandidate(KEY_ID_V3, RAW_KEY_V3_BASE64, true, serverPort);
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    String adminTokenV3 = fetchMasterAdminToken(http);
    String subjectV3 =
        createUserInRealm(
            http,
            adminTokenV3,
            TEST_REALM,
            "rotation-v3-" + UUID.randomUUID().toString().substring(0, 8),
            "RotationV3Password123!");
    LocalSessionFixture sessionsV3 =
        createTwoTargetSessionsAndControl(subjectV3, "rotation-control-v3");
    triggerAdministrativePasswordReset(http, adminTokenV3, subjectV3, "RotationV3Password456!");

    OriginOutboxRecord rejectedV3 =
        awaitOriginOutbox(
            subjectV3,
            row -> row.attemptCount() >= 1 && !"DELIVERED".equals(row.status()),
            REAL_CHAIN_TIMEOUT);
    assertThat(rejectedV3.status()).isIn("PENDING", "FAILED");
    assertThat(inboxRepository.findBySourceEventId(rejectedV3.sourceEventId())).isEmpty();
    assertThat(sessionRepository.findByPrincipalName(subjectV3)).hasSize(2);
    assertThat(sessionRepository.findById(sessionsV3.control().getId())).isNotNull();
    OAuth2AuthorizedClient retainedV3Client =
        authorizedClientService.loadAuthorizedClient("keycloak", subjectV3);
    assertThat(retainedV3Client).isNotNull();
    verify(oidcRevocationClient, times(2))
        .revokeRefreshToken(any(ClientRegistration.class), eq("test-refresh-token"));
  }

  // =========================================================================
  // Crash / Restart e RTO Saudável <= 60 s
  // =========================================================================

  @Test
  @Order(23)
  @DisplayName(
      "Crash/Restart: Evento CLAIMED abandonado é recuperado no restart e entregue com RTO <= 60 s")
  void crashAndRestart_reclaimsPendingAndClaimedEventsWithinHealthyRto() throws Exception {
    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, 1);
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    HttpClient http = HttpClient.newHttpClient();
    String adminToken = fetchMasterAdminToken(http);
    String alias = "crash-restart-" + UUID.randomUUID().toString().substring(0, 8);
    String subject = createUserInRealm(http, adminToken, TEST_REALM, alias, "CrashPassword123!");
    LocalSessionFixture sessions = createTwoTargetSessionsAndControl(subject);
    triggerAdministrativePasswordReset(http, adminToken, subject, "CrashPassword456!");

    OriginOutboxRecord pending =
        awaitOriginOutbox(
            subject,
            row -> row.attemptCount() >= 1 && !"DELIVERED".equals(row.status()),
            REAL_CHAIN_TIMEOUT);
    assertThat(inboxRepository.findBySourceEventId(pending.sourceEventId())).isEmpty();

    stopCandidate();
    markOriginClaimAbandoned(pending.sourceEventId(), Instant.now().minusSeconds(61));
    Instant restartStartedAt = Instant.now();
    ensureCandidate(KEY_ID_V2, RAW_KEY_V2_BASE64, true, serverPort);
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));

    RealDelivery delivery = awaitRealDelivery(subject, REAL_CHAIN_TIMEOUT);
    Duration rto = Duration.between(restartStartedAt, Instant.now());
    assertThat(rto)
        .as("RTO entre reinício da candidata e entrega terminal")
        .isLessThanOrEqualTo(REAL_CHAIN_TIMEOUT);
    assertThat(delivery.sourceEventId()).isEqualTo(pending.sourceEventId());
    assertCompleteRealDelivery(delivery, subject, sessions, 1);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private LocalSessionFixture createTwoTargetSessionsAndControl(String subject) {
    return createTwoTargetSessionsAndControl(subject, CONTROL_SUBJECT_B);
  }

  private LocalSessionFixture createTwoTargetSessionsAndControl(
      String subject, String controlSubject) {
    saveAuthorizedClient(subject);
    SelectiveTouchSessionRepository.SelectiveSession targetOne =
        createAuthenticatedSessionWithoutSavingGrant(subject);
    SelectiveTouchSessionRepository.SelectiveSession targetTwo =
        createAuthenticatedSessionWithoutSavingGrant(subject);
    SelectiveTouchSessionRepository.SelectiveSession control =
        createAuthenticatedSessionWithoutSavingGrant(controlSubject);
    sessionRepository.save(targetOne);
    sessionRepository.save(targetTwo);
    sessionRepository.save(control);
    assertThat(sessionRepository.findByPrincipalName(subject)).hasSize(2);
    assertThat(sessionRepository.findByPrincipalName(controlSubject)).hasSize(1);
    return new LocalSessionFixture(targetOne, targetTwo, control);
  }

  private void triggerHostedPasswordReset(String email, String newPassword) throws Exception {
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    AtomicReference<String> resetFlowCookies = new AtomicReference<>("");
    String codeVerifier =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(SecureRandom.getInstanceStrong().generateSeed(32));
    String codeChallenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
    URI authorizationUri =
        URI.create(
            keycloakBaseUrl
                + "/realms/"
                + TEST_REALM
                + "/protocol/openid-connect/auth"
                + "?response_type=code"
                + "&client_id="
                + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                + "&scope=openid"
                + "&state=hosted-reset-"
                + UUID.randomUUID()
                + "&code_challenge="
                + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
                + "&code_challenge_method=S256"
                + "&redirect_uri="
                + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8));

    HttpResponse<String> loginPage =
        followGetRedirects(http, authorizationUri, 5, resetFlowCookies);
    assertThat(loginPage.statusCode())
        .as("hosted reset login page; body=%s", loginPage.body())
        .isEqualTo(200);

    Matcher resetLink = RESET_CREDENTIALS_LINK.matcher(loginPage.body());
    assertThat(resetLink.find()).as("hosted reset credentials link").isTrue();
    URI resetUri = resolveHtmlUri(loginPage.uri(), resetLink.group(1));
    HttpResponse<String> resetPage = followGetRedirects(http, resetUri, 5, resetFlowCookies);
    assertThat(resetPage.statusCode())
        .as("hosted reset credentials page; body=%s", resetPage.body())
        .isEqualTo(200);

    Matcher resetAction = FORM_ACTION.matcher(resetPage.body());
    assertThat(resetAction.find()).as("hosted reset request form action").isTrue();
    URI resetActionUri = resolveHtmlUri(resetPage.uri(), resetAction.group(1));
    String previousMail = latestMailpitBody(http);
    HttpRequest.Builder resetRequest =
        HttpRequest.newBuilder(resetActionUri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)));
    addCookieHeader(resetRequest, resetFlowCookies.get());
    HttpResponse<String> resetRequested =
        http.send(resetRequest.build(), HttpResponse.BodyHandlers.ofString());
    resetFlowCookies.set(mergeCookies(resetFlowCookies.get(), resetRequested));
    assertThat(resetRequested.statusCode())
        .as("hosted reset request status; body=%s", resetRequested.body())
        .isIn(200, 302, 303);

    String resetMail =
        awaitValue(
            "hosted reset email containing action token",
            Duration.ofSeconds(30),
            () -> {
              String latest = latestMailpitBody(http);
              return !latest.equals(previousMail) && latest.contains("login-actions/action-token")
                  ? latest
                  : null;
            });
    URI rawActionTokenUri = URI.create(extractActionLink(resetMail));
    assertThat(rawActionTokenUri.getRawPath())
        .isEqualTo("/realms/" + TEST_REALM + "/login-actions/action-token");
    assertThat(rawActionTokenUri.getRawQuery()).contains("key=");
    URI actionTokenUri =
        URI.create(keycloakBaseUrl)
            .resolve(rawActionTokenUri.getRawPath() + "?" + rawActionTokenUri.getRawQuery());
    AtomicReference<String> actionFlowCookies = new AtomicReference<>("");
    HttpResponse<String> updatePasswordPage =
        followGetRedirects(http, actionTokenUri, 5, actionFlowCookies);
    assertThat(updatePasswordPage.statusCode())
        .as("hosted update-password page; body=%s", updatePasswordPage.body())
        .isEqualTo(200);

    Matcher updateAction = FORM_ACTION.matcher(updatePasswordPage.body());
    assertThat(updateAction.find()).as("hosted update-password form action").isTrue();
    URI updateActionUri = resolveHtmlUri(updatePasswordPage.uri(), updateAction.group(1));
    String updateBody =
        "password-new="
            + URLEncoder.encode(newPassword, StandardCharsets.UTF_8)
            + "&password-confirm="
            + URLEncoder.encode(newPassword, StandardCharsets.UTF_8)
            + "&logout-sessions=on";
    HttpRequest.Builder updateRequest =
        HttpRequest.newBuilder(updateActionUri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(updateBody));
    addCookieHeader(updateRequest, actionFlowCookies.get());
    HttpResponse<String> updated =
        http.send(updateRequest.build(), HttpResponse.BodyHandlers.ofString());
    actionFlowCookies.set(mergeCookies(actionFlowCookies.get(), updated));
    assertThat(updated.statusCode())
        .as("hosted update-password submission; body=%s", updated.body())
        .isIn(200, 302, 303);
  }

  private static HttpResponse<String> followGetRedirects(
      HttpClient http, URI initialUri, int maxRedirects, AtomicReference<String> cookies)
      throws Exception {
    URI current = initialUri;
    for (int redirect = 0; redirect <= maxRedirects; redirect++) {
      HttpResponse<String> response;
      try {
        HttpRequest.Builder request = HttpRequest.newBuilder(current).GET();
        addCookieHeader(request, cookies.get());
        response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      } catch (IOException connectionFailure) {
        throw new IOException("Hosted password-reset GET failed at " + current, connectionFailure);
      }
      cookies.set(mergeCookies(cookies.get(), response));
      if (response.statusCode() < 300 || response.statusCode() >= 400) {
        return response;
      }
      String location = response.headers().firstValue("Location").orElseThrow();
      current = response.uri().resolve(location.replace("&amp;", "&"));
    }
    throw new IllegalStateException("Too many redirects in hosted password-reset flow");
  }

  private static void addCookieHeader(HttpRequest.Builder request, String cookies) {
    if (cookies != null && !cookies.isBlank()) {
      request.header("Cookie", cookies);
    }
  }

  private static String mergeCookies(String previousCookies, HttpResponse<?> response) {
    Map<String, String> cookies = new LinkedHashMap<>();
    if (previousCookies != null && !previousCookies.isBlank()) {
      for (String part : previousCookies.split(";")) {
        int equals = part.indexOf('=');
        if (equals > 0) {
          cookies.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
        }
      }
    }
    for (String setCookie : response.headers().allValues("set-cookie")) {
      int end = setCookie.indexOf(';');
      String pair = end < 0 ? setCookie : setCookie.substring(0, end);
      int equals = pair.indexOf('=');
      if (equals > 0) {
        cookies.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
      }
    }
    return cookies.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(java.util.stream.Collectors.joining("; "));
  }

  private static URI resolveHtmlUri(URI base, String href) {
    return base.resolve(href.replace("&amp;", "&"));
  }

  private static String latestMailpitBody(HttpClient http) throws Exception {
    String host = "localhost".equals(MAILPIT.getHost()) ? "127.0.0.1" : MAILPIT.getHost();
    URI latest =
        URI.create("http://" + host + ":" + MAILPIT.getMappedPort(8025) + "/view/latest.html");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(latest).GET().build(), HttpResponse.BodyHandlers.ofString());
    return response.statusCode() == 200 ? response.body() : "";
  }

  private static String extractActionLink(String emailContent) {
    Matcher href = ACTION_TOKEN_HREF.matcher(emailContent);
    if (href.find()) {
      return href.group(1).replace("&amp;", "&");
    }
    Matcher text = ACTION_TOKEN_TEXT.matcher(emailContent);
    if (text.find()) {
      return text.group(1).replace("&amp;", "&");
    }
    throw new IllegalStateException("Could not extract hosted password-reset action token");
  }

  private static void triggerAdministrativePasswordReset(
      HttpClient http, String adminToken, String userId, String newPassword) throws Exception {
    String url =
        keycloakBaseUrl
            + "/admin/realms/"
            + TEST_REALM
            + "/users/"
            + URLEncoder.encode(userId, StandardCharsets.UTF_8)
            + "/reset-password";
    String body =
        """
                {
                    "type": "password",
                    "value": "%s",
                    "temporary": false
                }
                """
            .formatted(newPassword);
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("administrative password reset status; body=%s", response.body())
        .isEqualTo(204);
  }

  private RealDelivery awaitRealDelivery(String subject, Duration timeout) throws Exception {
    OriginOutboxRecord origin =
        awaitOriginOutbox(subject, row -> "DELIVERED".equals(row.status()), timeout);
    PasswordResetEventInboxEntity destination =
        awaitValue(
            "backend inbox DELIVERED for " + origin.sourceEventId(),
            timeout,
            () ->
                inboxRepository
                    .findBySourceEventId(origin.sourceEventId())
                    .filter(
                        row ->
                            PasswordResetEventInboxEntity.STATUS_DELIVERED.equals(row.getStatus()))
                    .orElse(null));
    return new RealDelivery(origin.sourceEventId(), origin, destination);
  }

  private OriginOutboxRecord awaitOriginOutbox(
      String subject, Predicate<OriginOutboxRecord> predicate, Duration timeout) throws Exception {
    return awaitValue(
        "candidate outbox row for subject " + subject,
        timeout,
        () -> findLatestOriginOutbox(subject).filter(predicate).orElse(null));
  }

  private Optional<OriginOutboxRecord> findLatestOriginOutbox(String subject) throws SQLException {
    String sql =
        """
                SELECT source_event_id, subject, event_type, status, attempt_count, last_error,
                       next_attempt_at
                  FROM public.betufu_password_reset_outbox
                 WHERE subject = ?
                 ORDER BY created_at DESC
                 LIMIT 1
                """;
    try (Connection connection = openCandidateDatabase();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, subject);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new OriginOutboxRecord(
                rows.getString("source_event_id"),
                rows.getString("subject"),
                rows.getString("event_type"),
                rows.getString("status"),
                rows.getInt("attempt_count"),
                rows.getString("last_error"),
                rows.getTimestamp("next_attempt_at") == null
                    ? null
                    : rows.getTimestamp("next_attempt_at").toInstant()));
      }
    }
  }

  private static void requeueOriginOutbox(String sourceEventId) throws SQLException {
    String sql =
        """
                UPDATE public.betufu_password_reset_outbox
                   SET status = 'PENDING', delivered_at = NULL, claimed_at = NULL,
                       claim_instance_id = NULL, next_attempt_at = NULL, updated_at = ?
                 WHERE source_event_id = ?
                """;
    try (Connection connection = openCandidateDatabase();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setTimestamp(1, Timestamp.from(Instant.now()));
      statement.setString(2, sourceEventId);
      assertThat(statement.executeUpdate()).isEqualTo(1);
    }
  }

  private static void markOriginClaimAbandoned(String sourceEventId, Instant claimedAt)
      throws SQLException {
    String sql =
        """
                UPDATE public.betufu_password_reset_outbox
                   SET status = 'CLAIMED', claimed_at = ?, claim_instance_id = 'crashed-wave9-worker',
                       next_attempt_at = NULL, updated_at = ?
                 WHERE source_event_id = ?
                """;
    try (Connection connection = openCandidateDatabase();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setTimestamp(1, Timestamp.from(claimedAt));
      statement.setTimestamp(2, Timestamp.from(claimedAt));
      statement.setString(3, sourceEventId);
      assertThat(statement.executeUpdate()).isEqualTo(1);
    }
  }

  private void assertCompleteRealDelivery(
      RealDelivery delivery,
      String subject,
      LocalSessionFixture sessions,
      int expectedRemoteRevocations) {
    assertThat(delivery.origin().sourceEventId())
        .isEqualTo(delivery.destination().getSourceEventId());
    assertThat(delivery.origin().subject()).isEqualTo(subject);
    assertThat(delivery.origin().status()).isEqualTo("DELIVERED");
    assertThat(delivery.destination().getStatus())
        .isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(sessionRepository.findByPrincipalName(subject)).isEmpty();
    assertThat(sessionRepository.findById(sessions.targetOne().getId())).isNull();
    assertThat(sessionRepository.findById(sessions.targetTwo().getId())).isNull();
    assertThat(sessionRepository.findById(sessions.control().getId())).isNotNull();
    OAuth2AuthorizedClient removedClient =
        authorizedClientService.loadAuthorizedClient("keycloak", subject);
    assertThat(removedClient).isNull();
    verify(oidcRevocationClient, times(expectedRemoteRevocations))
        .revokeRefreshToken(any(ClientRegistration.class), eq("test-refresh-token"));
  }

  private static <T> T awaitValue(String description, Duration timeout, CheckedSupplier<T> supplier)
      throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    T value = null;
    while (System.nanoTime() < deadline) {
      value = supplier.get();
      if (value != null) {
        return value;
      }
      Thread.sleep(200L);
    }
    assertThat(value).as(description).isNotNull();
    return value;
  }

  private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(
      String subject) {
    saveAuthorizedClient(subject);
    return createAuthenticatedSessionWithoutSavingGrant(subject);
  }

  private SelectiveTouchSessionRepository.SelectiveSession
      createAuthenticatedSessionWithoutSavingGrant(String subject) {
    SelectiveTouchSessionRepository.SelectiveSession session = sessionRepository.createSession();
    Instant now = clock.instant();

    OidcIdToken idToken =
        OidcIdToken.withTokenValue("termination-token-" + subject)
            .issuer("https://idp.invalid")
            .subject(subject)
            .claim("preferred_username", "user-" + subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();

    DefaultOidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, IdTokenClaimNames.SUB);

    OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);

    session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
    session.setAttribute(LAST_VALID_EPOCH_SECOND_ATTRIBUTE, now.getEpochSecond());
    session.setAttribute(LAST_VALID_NANO_ATTRIBUTE, now.getNano());
    session.setAttribute(APPROVED_ACTIVITY_ATTRIBUTE, now);

    return session;
  }

  @FunctionalInterface
  private interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  private record OriginOutboxRecord(
      String sourceEventId,
      String subject,
      String eventType,
      String status,
      int attemptCount,
      String lastError,
      Instant nextAttemptAt) {}

  private record RealDelivery(
      String sourceEventId, OriginOutboxRecord origin, PasswordResetEventInboxEntity destination) {}

  private record ReconcilerExecution(int exitCode, String output) {}

  private record LocalSessionFixture(
      SelectiveTouchSessionRepository.SelectiveSession targetOne,
      SelectiveTouchSessionRepository.SelectiveSession targetTwo,
      SelectiveTouchSessionRepository.SelectiveSession control) {}

  private void saveAuthorizedClient(String principalName) {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
    Instant issuedAt = clock.instant();
    OAuth2AuthorizedClient client =
        new OAuth2AuthorizedClient(
            registration,
            principalName,
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                issuedAt,
                issuedAt.plusSeconds(300)),
            new OAuth2RefreshToken("test-refresh-token", issuedAt));
    authorizedClientService.saveAuthorizedClient(
        client, new TestingAuthenticationToken(principalName, "not-used", "ROLE_USER"));
  }

  private static String fetchMasterAdminToken(HttpClient http) throws Exception {
    String tokenEndpoint = keycloakBaseUrl + "/realms/master/protocol/openid-connect/token";
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

    assertThat(response.statusCode())
        .as("master admin token status; body=%s", response.body())
        .isEqualTo(200);
    return JSON.readTree(response.body()).path("access_token").asText();
  }

  private static String createUserInRealm(
      HttpClient http, String adminToken, String realm, String username, String password)
      throws Exception {
    String userUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/users";
    String userJson =
        """
                {
                    "username": "%s",
                    "email": "%s@betufu.local",
                    "enabled": true,
                    "emailVerified": true,
                    "credentials": [
                        {
                            "type": "password",
                            "value": "%s",
                            "temporary": false
                        }
                    ]
                }
                """
            .formatted(username, username, password);

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(userUrl))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(userJson))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isIn(201, 409);

    // Consulta ID do usuário criado
    HttpResponse<String> listResp =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        userUrl
                            + "?username="
                            + URLEncoder.encode(username, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    JsonNode array = JSON.readTree(listResp.body());
    return array.get(0).path("id").asText();
  }

  private static void ensureRealmRoleExists(
      HttpClient http, String adminToken, String realm, String roleName) throws Exception {
    String roleUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/roles";
    String body = "{\"name\":\"" + roleName + "\"}";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(roleUrl))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("realm role creation status; body=%s", response.body())
        .isIn(201, 409);
  }

  private static void assignRealmRoleToUser(
      HttpClient http, String adminToken, String realm, String userId, String roleName)
      throws Exception {
    String roleUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/roles/" + roleName;
    HttpResponse<String> roleResp =
        http.send(
            HttpRequest.newBuilder(URI.create(roleUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(roleResp.statusCode())
        .as("realm role lookup status; body=%s", roleResp.body())
        .isEqualTo(200);

    JsonNode roleNode = JSON.readTree(roleResp.body());
    String mappingUrl =
        keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
    HttpResponse<String> mappingResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(mappingUrl))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[" + roleNode.toString() + "]"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(mappingResponse.statusCode())
        .as("realm role mapping status; body=%s", mappingResponse.body())
        .isEqualTo(204);
  }

  private static void assignClientRoleToUser(
      HttpClient http,
      String adminToken,
      String realm,
      String userId,
      String clientName,
      String roleName)
      throws Exception {
    String clientsUrl =
        keycloakBaseUrl + "/admin/realms/" + realm + "/clients?clientId=" + clientName;
    HttpResponse<String> clientsResp =
        http.send(
            HttpRequest.newBuilder(URI.create(clientsUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(clientsResp.statusCode())
        .as("realm-management client lookup status; body=%s", clientsResp.body())
        .isEqualTo(200);

    JsonNode clientsArray = JSON.readTree(clientsResp.body());
    assertThat(clientsArray).hasSize(1);
    String clientDbId = clientsArray.get(0).path("id").asText();

    String roleUrl =
        keycloakBaseUrl
            + "/admin/realms/"
            + realm
            + "/clients/"
            + clientDbId
            + "/roles/"
            + roleName;
    HttpResponse<String> roleResp =
        http.send(
            HttpRequest.newBuilder(URI.create(roleUrl))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(roleResp.statusCode())
        .as("client role lookup status; body=%s", roleResp.body())
        .isEqualTo(200);

    JsonNode roleNode = JSON.readTree(roleResp.body());
    String mappingUrl =
        keycloakBaseUrl
            + "/admin/realms/"
            + realm
            + "/users/"
            + userId
            + "/role-mappings/clients/"
            + clientDbId;
    HttpResponse<String> mappingResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(mappingUrl))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[" + roleNode.toString() + "]"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(mappingResponse.statusCode())
        .as("client role mapping status; body=%s", mappingResponse.body())
        .isEqualTo(204);
  }

  private static void ensureDirectAccessClientExists(
      HttpClient http, String adminToken, String realm, String clientId) throws Exception {
    ensureDirectAccessClientExists(http, adminToken, realm, clientId, false, null);
  }

  private static void ensureDirectAccessClientExists(
      HttpClient http,
      String adminToken,
      String realm,
      String clientId,
      boolean includeRealmManagementAudience,
      Integer accessTokenLifespanSeconds)
      throws Exception {
    String clientUrl = keycloakBaseUrl + "/admin/realms/" + realm + "/clients";
    String attributes =
        accessTokenLifespanSeconds == null
            ? "{}"
            : "{\"access.token.lifespan\":\"" + accessTokenLifespanSeconds + "\"}";
    String protocolMappers =
        includeRealmManagementAudience
            ? """
                    ,"protocolMappers":[
                      {
                        "name":"realm-management-audience",
                        "protocol":"openid-connect",
                        "protocolMapper":"oidc-audience-mapper",
                        "consentRequired":false,
                        "config":{
                          "included.client.audience":"realm-management",
                          "id.token.claim":"false",
                          "access.token.claim":"true",
                          "introspection.token.claim":"true"
                        }
                      }
                    ]
                    """
            : "";
    String clientJson =
        """
                {
                    "clientId": "%s",
                    "enabled": true,
                    "publicClient": true,
                    "directAccessGrantsEnabled": true,
                    "attributes": %s%s
                }
                """
            .formatted(clientId, attributes, protocolMappers);

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(clientUrl))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(clientJson))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("direct-access client creation status; body=%s", response.body())
        .isIn(201, 409);
  }

  private static String fetchUserTokenDirect(
      HttpClient http, String realm, String clientId, String username, String password)
      throws Exception {
    return fetchUserTokenDirectAtBaseUrl(
        http, keycloakBaseUrl, realm, clientId, username, password);
  }

  private static String fetchUserTokenDirectAtBaseUrl(
      HttpClient http,
      String baseUrl,
      String realm,
      String clientId,
      String username,
      String password)
      throws Exception {
    String tokenEndpoint = baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    String body =
        "grant_type=password"
            + "&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&username="
            + URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password="
            + URLEncoder.encode(password, StandardCharsets.UTF_8);

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("direct token status for %s; body=%s", username, response.body())
        .isEqualTo(200);
    return JSON.readTree(response.body()).path("access_token").asText();
  }

  private static String issueAuthorizedRecoveryToken(
      HttpClient http,
      String adminToken,
      String prefix,
      String password,
      String tokenBaseUrl,
      Integer accessTokenLifespanSeconds)
      throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String username = prefix + "-" + suffix;
    String userId = createUserInRealm(http, adminToken, TEST_REALM, username, password);
    assignClientRoleToUser(
        http, adminToken, TEST_REALM, userId, "realm-management", "manage-realm");
    String clientId = prefix + "-client-" + suffix;
    ensureDirectAccessClientExists(
        http, adminToken, TEST_REALM, clientId, false, accessTokenLifespanSeconds);
    return fetchUserTokenDirectAtBaseUrl(
        http, tokenBaseUrl, TEST_REALM, clientId, username + "@betufu.local", password);
  }

  private static JsonNode assertSignedTokenFixture(
      String jwt,
      String expectedIssuer,
      boolean expectRealmManagementAudience,
      boolean requireActive)
      throws Exception {
    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
    JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertThat(header.path("alg").asText())
        .as("a negativa precisa partir de assinatura assimetrica real do Keycloak")
        .isEqualTo("RS256");
    assertThat(claims.path("iss").asText()).isEqualTo(expectedIssuer);
    assertThat(tokenHasAudience(claims, "realm-management"))
        .isEqualTo(expectRealmManagementAudience);
    if (requireActive) {
      assertThat(claims.path("exp").asLong())
          .as("token da fixture precisa estar ativo antes da mutacao isolada")
          .isGreaterThan(Instant.now().getEpochSecond());
    }
    return claims;
  }

  private static boolean tokenHasAudience(JsonNode claims, String audience) {
    JsonNode audiences = claims.path("aud");
    if (audiences.isTextual()) {
      return audience.equals(audiences.asText());
    }
    if (audiences.isArray()) {
      for (JsonNode value : audiences) {
        if (audience.equals(value.asText())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tokenHasRealmRole(JsonNode claims, String role) {
    return jsonArrayContains(claims.path("realm_access").path("roles"), role);
  }

  private static boolean tokenHasClientRole(JsonNode claims, String clientId, String role) {
    return jsonArrayContains(claims.path("resource_access").path(clientId).path("roles"), role);
  }

  private static boolean jsonArrayContains(JsonNode values, String expected) {
    if (!values.isArray()) {
      return false;
    }
    for (JsonNode value : values) {
      if (expected.equals(value.asText())) {
        return true;
      }
    }
    return false;
  }

  private static void awaitTokenExpiry(long expiresAtEpochSecond) throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(8);
    while (Instant.now().getEpochSecond() <= expiresAtEpochSecond
        && Instant.now().isBefore(deadline)) {
      Thread.sleep(100L);
    }
    assertThat(Instant.now().getEpochSecond())
        .as("token realmente assinado precisa estar expirado antes da chamada")
        .isGreaterThan(expiresAtEpochSecond);
  }

  private static String alternateLoopbackBaseUrl() {
    URI canonical = URI.create(keycloakBaseUrl);
    assertThat(canonical.getHost())
        .as("a fixture de issuer alternativo exige Docker local em loopback")
        .isEqualTo("127.0.0.1");
    return "http://localhost:" + canonical.getPort();
  }

  private static String tamperJwtSignature(String jwt) {
    String[] parts = jwt.split("\\.");
    if (parts.length >= 3) {
      byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);
      if (sigBytes.length > 0) {
        sigBytes[0] = (byte) (sigBytes[0] ^ 0xFF);
      }
      return parts[0]
          + "."
          + parts[1]
          + "."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
    }
    return jwt + "tampered";
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    @Primary
    MutableTestClock mutableTestClock() {
      return new MutableTestClock(Instant.EPOCH, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    OidcRevocationClient mockOidcRevocationClient() {
      return mock(OidcRevocationClient.class);
    }

    @Bean
    @Primary
    AuditRecorder testAuditRecorder() {
      return entry -> {};
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-termination-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Password Reset Session Termination IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(PasswordResetEventProperties.class)
  static class PasswordResetBindingConfiguration {}

  static final class MutableTestClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    private MutableTestClock(Instant instant, ZoneId zone) {
      this(new AtomicReference<>(Objects.requireNonNull(instant)), zone);
    }

    private MutableTestClock(AtomicReference<Instant> instant, ZoneId zone) {
      this.instant = instant;
      this.zone = Objects.requireNonNull(zone);
    }

    void set(Instant newInstant) {
      instant.set(Objects.requireNonNull(newInstant));
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return new MutableTestClock(instant, newZone);
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
