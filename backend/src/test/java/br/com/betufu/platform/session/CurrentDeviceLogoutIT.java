package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.betufu.platform.security.SecurityConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Proves the current-device logout of Plan 01-22, Task 2. */
@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      SessionSecurityConfiguration.class,
      SessionController.class,
      CurrentDeviceLogoutController.class,
      OidcSessionRevocationService.AtomicAuthorizedClientServiceConfiguration.class,
      CurrentDeviceLogoutIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.session.data.redis.configure-action=none",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
      "BETUFU_DB_JDBC_URL=jdbc:postgresql://database.invalid/betufu",
      "BETUFU_DB_USER=unused",
      "BETUFU_DB_PASSWORD=unused",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-current-device-logout-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
class CurrentDeviceLogoutIT {

  private static final String LOGOUT_PATH = "/api/v1/session/logout";
  private static final String SESSION_PATH = "/api/v1/session";
  private static final String SESSION_COOKIE_NAME = "SESSION";
  private static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final String XSRF_HEADER_NAME = "X-XSRF-TOKEN";
  private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
  private static final String ISSUER = "https://idp.invalid";
  private static final String END_SESSION_ENDPOINT = ISSUER + "/protocol/openid-connect/logout";
  private static final String REGISTRATION_WITH_END_SESSION = "keycloak";
  private static final String REGISTRATION_WITHOUT_END_SESSION = "keycloak-no-end-session";
  private static final String CLIENT_ID = "betufu-current-device-logout-it";
  private static final String SUBJECT_A = "current-device-logout-it-user-a";
  private static final String SUBJECT_B = "current-device-logout-it-user-b";
  private static final String SECRET_REFRESH_TOKEN = "refresh-token-must-never-be-published";
  private static final String SECRET_ACCESS_TOKEN = "access-token-must-never-be-published";
  private static final String REDIS_PASSWORD = "betufu-current-device-logout-it-only";
  private static final int REDIS_PORT = 6379;
  private static final String REDIS_IMAGE_REFERENCE =
      "docker.io/library/redis:8.10.0-trixie@sha256:"
          + "344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366";

  private static final DockerImageName REDIS_IMAGE =
      DockerImageName.parse(REDIS_IMAGE_REFERENCE).asCompatibleSubstituteFor("redis");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(REDIS_IMAGE)
          .withImagePullPolicy(imageName -> false)
          .withExposedPorts(REDIS_PORT)
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
          .waitingFor(Wait.forListeningPort());

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
    registry.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(REDIS_PORT));
    registry.add("BETUFU_REDIS_PASSWORD", () -> REDIS_PASSWORD);
  }

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private StringRedisTemplate redis;

  @Autowired private MutableTestClock clock;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired private OAuth2AuthorizedClientService authorizedClientService;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  @Autowired
  @Qualifier("springSessionRepositoryFilter")
  private Filter springSessionRepositoryFilter;

  private MockMvc mockMvc;
  private SelectiveTouchSessionRepository repository;

  @BeforeEach
  void prepareRedisAndMockMvc() {
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
    clock.set(Instant.now());
    repository = (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSessionRepositoryFilter, springSecurityFilterChain)
            .build();
  }

  @Test
  void applicationUsesOneAtomicAuthorizedClientService() {
    Map<String, OAuth2AuthorizedClientService> beans =
        applicationContext.getBeansOfType(OAuth2AuthorizedClientService.class);

    assertThat(beans).hasSize(1);
    assertThat(authorizedClientService)
        .isSameAs(beans.values().iterator().next())
        .isInstanceOf(OidcSessionRevocationService.AtomicOAuth2AuthorizedClientService.class);
  }

  /** D-08 and D-09: only the current device ends. */
  @Test
  void logoutOfSessionAEndsOnlyAAndLeavesBUsable() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA = createLifecycleSession();
    SelectiveTouchSessionRepository.SelectiveSession sessionB = createLifecycleSession();
    assertThat(sessionB.getId()).isNotEqualTo(sessionA.getId());

    Cookie cookieA = sessionCookie(sessionA.getId());
    Cookie cookieB = sessionCookie(sessionB.getId());

    MvcResult result = performLogout(cookieA, SUBJECT_A, REGISTRATION_WITH_END_SESSION);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    assertThat(redis.hasKey(sessionKey(sessionA.getId())))
        .as("session A must be gone from Redis")
        .isFalse();
    assertThat(redis.hasKey(sessionKey(sessionB.getId())))
        .as("session B must survive the logout of A")
        .isTrue();

    MvcResult bStillWorks =
        mockMvc
            .perform(
                get(SESSION_PATH)
                    .cookie(cookieB)
                    .with(oidcPrincipal(SUBJECT_B, REGISTRATION_WITH_END_SESSION)))
            .andReturn();
    assertThat(bStillWorks.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(bStillWorks)).contains("\"authenticated\":true");

    MvcResult aIsAnonymous = mockMvc.perform(get(SESSION_PATH).cookie(cookieA)).andReturn();
    assertThat(body(aIsAnonymous)).contains("\"authenticated\":false");
  }

  /** The local grant becomes unusable, without revoking at the provider. */
  @Test
  void logoutRemovesTheAuthorizedClientOfTheCurrentDeviceOnly() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA = createLifecycleSession();
    saveAuthorizedClient(SUBJECT_A);
    saveAuthorizedClient(SUBJECT_B);
    assertThat(loadAuthorizedClient(SUBJECT_A)).isNotNull();

    performLogout(sessionCookie(sessionA.getId()), SUBJECT_A, REGISTRATION_WITH_END_SESSION);

    assertThat(loadAuthorizedClient(SUBJECT_A))
        .as("the current device's authorized client must be removed")
        .isNull();
    assertThat(loadAuthorizedClient(SUBJECT_B))
        .as("another principal's authorized client must be untouched")
        .isNotNull();
  }

  /** POST + CSRF only; no mutable GET, and Task 1's permitAll must not leak onto this path. */
  @Test
  void endpointAcceptsOnlyAuthenticatedCsrfProtectedPost() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    Cookie cookie = sessionCookie(session.getId());
    Cookie xsrfCookie = materializeXsrf(cookie);

    int withoutCsrf =
        mockMvc
            .perform(
                post(LOGOUT_PATH)
                    .cookie(cookie)
                    .with(oidcPrincipal(SUBJECT_A, REGISTRATION_WITH_END_SESSION)))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(withoutCsrf).as("POST without the XSRF header").isEqualTo(403);

    int wrongCsrf =
        mockMvc
            .perform(
                post(LOGOUT_PATH)
                    .cookie(cookie, xsrfCookie)
                    .header(XSRF_HEADER_NAME, "invalid-xsrf-token")
                    .with(oidcPrincipal(SUBJECT_A, REGISTRATION_WITH_END_SESSION)))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(wrongCsrf).as("POST with a mismatched XSRF header").isEqualTo(403);

    assertThat(redis.hasKey(sessionKey(session.getId())))
        .as("a rejected logout must not end the session")
        .isTrue();

    int authenticatedGet =
        mockMvc
            .perform(
                get(LOGOUT_PATH)
                    .cookie(cookie)
                    .with(oidcPrincipal(SUBJECT_A, REGISTRATION_WITH_END_SESSION)))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(authenticatedGet).as("no mutable GET").isEqualTo(405);

    int anonymousGet = mockMvc.perform(get(LOGOUT_PATH)).andReturn().getResponse().getStatus();
    assertThat(anonymousGet)
        .as("the GET permitAll of /api/v1/session must not extend to /logout")
        .isBetween(300, 399);

    assertThat(redis.hasKey(sessionKey(session.getId())))
        .as("none of the rejected requests may end the session")
        .isTrue();
  }

  /** A coordination failure keeps the session terminated and stays sanitized. */
  @Test
  void unavailableEndSessionEndpointStillTerminatesAndReturnsASanitizedError() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    saveAuthorizedClient(SUBJECT_A, REGISTRATION_WITHOUT_END_SESSION);

    MvcResult result =
        performLogout(sessionCookie(session.getId()), SUBJECT_A, REGISTRATION_WITHOUT_END_SESSION);

    assertThat(result.getResponse().getStatus()).isEqualTo(502);
    assertThat(body(result))
        .isEqualTo("{\"status\":\"END_SESSION_UNAVAILABLE\",\"endSessionUrl\":null}");

    assertThat(redis.hasKey(sessionKey(session.getId())))
        .as("a coordination failure must never revive the session")
        .isFalse();
    assertThat(loadAuthorizedClient(SUBJECT_A, REGISTRATION_WITHOUT_END_SESSION))
        .as("the authorized client is removed before coordination is attempted")
        .isNull();
  }

  /** No authentication material may reach the browser, in the body or the returned URL. */
  @Test
  void responseCarriesNoTokenAndNoSessionIdentifier() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    saveAuthorizedClient(SUBJECT_A);

    MvcResult result =
        performLogout(sessionCookie(session.getId()), SUBJECT_A, REGISTRATION_WITH_END_SESSION);
    String body = body(result);

    assertThat(body)
        .doesNotContain(SECRET_REFRESH_TOKEN)
        .doesNotContain(SECRET_ACCESS_TOKEN)
        .doesNotContain("id_token_hint")
        .doesNotContain("access_token")
        .doesNotContain("refresh_token")
        .doesNotContain(session.getId());
    // Exact bytes: client_id replaces id_token_hint, and the redirect is same-origin.
    assertThat(body)
        .isEqualTo(
            "{\"status\":\"LOGGED_OUT\",\"endSessionUrl\":\""
                + END_SESSION_ENDPOINT
                + "?client_id="
                + CLIENT_ID
                + "&post_logout_redirect_uri=http://localhost/\"}");
  }

  private MvcResult performLogout(Cookie cookie, String subject, String registrationId)
      throws Exception {
    Cookie xsrfCookie = materializeXsrf(cookie);
    return mockMvc
        .perform(
            post(LOGOUT_PATH)
                .cookie(cookie, xsrfCookie)
                .header(XSRF_HEADER_NAME, xsrfCookie.getValue())
                .with(oidcPrincipal(subject, registrationId)))
        .andReturn();
  }

  private Cookie materializeXsrf(Cookie sessionCookie) throws Exception {
    MvcResult result = mockMvc.perform(get("/login").cookie(sessionCookie)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Cookie xsrfCookie = result.getResponse().getCookie(XSRF_COOKIE_NAME);
    assertThat(xsrfCookie).as("real XSRF cookie from the login page").isNotNull();
    return xsrfCookie;
  }

  private void saveAuthorizedClient(String principalName) {
    saveAuthorizedClient(principalName, REGISTRATION_WITH_END_SESSION);
  }

  private void saveAuthorizedClient(String principalName, String registrationId) {
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId(registrationId);
    Instant issuedAt = Instant.now();
    OAuth2AuthorizedClient client =
        new OAuth2AuthorizedClient(
            registration,
            principalName,
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                SECRET_ACCESS_TOKEN,
                issuedAt,
                issuedAt.plusSeconds(300)),
            new OAuth2RefreshToken(SECRET_REFRESH_TOKEN, issuedAt));
    authorizedClientService.saveAuthorizedClient(
        client, new TestingAuthenticationToken(principalName, "not-used", "ROLE_USER"));
  }

  private OAuth2AuthorizedClient loadAuthorizedClient(String principalName) {
    return loadAuthorizedClient(principalName, REGISTRATION_WITH_END_SESSION);
  }

  private OAuth2AuthorizedClient loadAuthorizedClient(String principalName, String registrationId) {
    return authorizedClientService.loadAuthorizedClient(registrationId, principalName);
  }

  private SelectiveTouchSessionRepository.SelectiveSession createLifecycleSession() {
    SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
    Instant initializedAt = clock.instant();
    session.setAttribute(
        SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
        initializedAt.getEpochSecond());
    session.setAttribute(
        SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE, initializedAt.getNano());
    session.setAttribute(SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE, 0);
    session.setAttribute(
        SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, initializedAt);
    repository.save(session);
    return session;
  }

  private RequestPostProcessor oidcPrincipal(String subject, String registrationId) {
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId(registrationId);
    assertThat(registration).isNotNull();
    return oidcLogin()
        .clientRegistration(registration)
        .idToken(
            token ->
                token
                    .claim(IdTokenClaimNames.ISS, ISSUER)
                    .claim(IdTokenClaimNames.SUB, subject)
                    .claim("preferred_username", subject)
                    .claim("amr", List.of("pwd")));
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static String sessionKey(String sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }

  private static Cookie sessionCookie(String sessionId) {
    String encoded = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    return new Cookie(SESSION_COOKIE_NAME, encoded);
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
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          registration(
              REGISTRATION_WITH_END_SESSION, Map.of("end_session_endpoint", END_SESSION_ENDPOINT)),
          registration(REGISTRATION_WITHOUT_END_SESSION, Map.of()));
    }

    private static ClientRegistration registration(
        String registrationId, Map<String, Object> metadata) {
      return ClientRegistration.withRegistrationId(registrationId)
          .clientId(CLIENT_ID)
          .clientSecret("unused")
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
          .scope("openid")
          .authorizationUri(ISSUER + "/authorize")
          .tokenUri(ISSUER + "/token")
          .jwkSetUri(ISSUER + "/jwks")
          .issuerUri(ISSUER)
          .providerConfigurationMetadata(metadata)
          .userNameAttributeName(IdTokenClaimNames.SUB)
          .clientName("BETUFU Current Device Logout IT " + registrationId)
          .build();
    }
  }

  /** Local copy, for the same nest-access reason documented in {@code SessionControllerIT}. */
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
