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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the published session contract of Plan 01-22, Task 1.
 *
 * <p>The context lists its classes explicitly, mirroring {@code RedisSessionIT}. That matters here:
 * {@code RedisSessionIT} owns a probe controller that also maps {@code /api/v1/session}, and only
 * the explicit class lists keep the two out of the same context.
 */
@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      SessionSecurityConfiguration.class,
      SessionController.class,
      SessionControllerIT.TestApplication.class
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
      "BETUFU_OIDC_CLIENT_ID=betufu-session-controller-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
class SessionControllerIT {

  private static final String SESSION_PATH = "/api/v1/session";
  private static final String OTHER_API_PATH = "/api/v1/tracer/identity/current";
  private static final String SESSION_COOKIE_NAME = "SESSION";
  private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
  private static final String DISPLAY_NAME = "aluno.ufu";
  private static final String SUBJECT = "session-controller-it-user";
  private static final String ISSUER = "https://idp.invalid";
  private static final String ACR = "urn:betufu:acr:mfa";
  private static final Duration SHORT_TEST_TTL = Duration.ofMinutes(5);
  private static final String REDIS_PASSWORD = "betufu-session-controller-it-only";
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

  /** Criterion 1. */
  @Test
  void anonymousQueryPublishesTheUnauthenticatedContractWithoutCreatingASession() throws Exception {
    long keysBefore = sessionKeyCount();

    MvcResult result = mockMvc.perform(get(SESSION_PATH)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(result))
        .isEqualTo(
            "{\"authenticated\":false,"
                + "\"displayName\":null,"
                + "\"idleTimeout\":\"PT8H\","
                + "\"absoluteTimeout\":null,"
                + "\"lastValidActivityAt\":null}");
    assertThat(result.getResponse().getCookie(SESSION_COOKIE_NAME))
        .as("anonymous query must not hand out a session cookie")
        .isNull();
    assertThat(sessionKeyCount())
        .as("anonymous query must not create a Redis session key")
        .isEqualTo(keysBefore);
  }

  /** Criteria 2, 3 and 4. */
  @Test
  void authenticatedQueryPublishesOnlyTheApprovedProjection() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    Cookie cookie = sessionCookie(session.getId());

    MvcResult result = mockMvc.perform(authenticated(get(SESSION_PATH), cookie)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String body = body(result);

    Instant lastValidActivityAt = lastValidActivity(repository.findById(session.getId()));
    assertThat(body)
        .isEqualTo(
            "{\"authenticated\":true,"
                + "\"displayName\":\""
                + DISPLAY_NAME
                + "\","
                + "\"idleTimeout\":\"PT8H\","
                + "\"absoluteTimeout\":null,"
                + "\"lastValidActivityAt\":\""
                + lastValidActivityAt
                + "\"}");

    // Criterion 3: the literal ISO-8601 form, not a deserialized Duration. Flipping
    // WRITE_DURATIONS_AS_TIMESTAMPS back on must break this.
    assertThat(body).contains("\"idleTimeout\":\"PT8H\"");
    // Criterion 4: D-06 requires the absent absolute deadline to be explicit, not omitted.
    assertThat(body).contains("\"absoluteTimeout\":null");

    // Criterion 2: no authentication material and no raw claims may reach the browser.
    assertThat(body)
        .doesNotContain(session.getId())
        .doesNotContain("access_token")
        .doesNotContain("id_token")
        .doesNotContain("refresh_token")
        .doesNotContain("SESSION")
        .doesNotContain("mfaAuthenticated")
        .doesNotContain(ACR)
        .doesNotContain("\"acr\"")
        .doesNotContain("\"amr\"")
        .doesNotContain(SUBJECT)
        .doesNotContain(ISSUER);
    assertThat(result.getResponse().getCookie(SESSION_COOKIE_NAME))
        .as("query must not rotate or re-issue the session cookie")
        .isNull();
  }

  /** Criterion 5. */
  @Test
  void queryNeverAdvancesThePersistedDeadlineOrTtl() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    Cookie cookie = sessionCookie(session.getId());
    shortenTtl(session.getId());

    long pttlBefore = pttl(session.getId());
    Instant lastValidBefore = lastValidActivity(repository.findById(session.getId()));

    // The clock moves forward so a renewing implementation would have something to write.
    clock.set(clock.instant().plusSeconds(120));
    MvcResult result = mockMvc.perform(authenticated(get(SESSION_PATH), cookie)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    assertThat(pttl(session.getId()))
        .as("Redis PTTL after the session query")
        .isPositive()
        .isLessThanOrEqualTo(pttlBefore);
    assertThat(lastValidActivity(repository.findById(session.getId())))
        .as("persisted last valid activity after the session query")
        .isEqualTo(lastValidBefore);
  }

  /** Criterion 6. */
  @Test
  void expiredSessionPublishesTheUnauthenticatedContractWithoutResurrection() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
    Cookie cookie = sessionCookie(session.getId());
    Instant lastValidBefore = lastValidActivity(repository.findById(session.getId()));

    // expiryFor() ties the Redis TTL to the same instant as the policy deadline, so the two
    // normally vanish together. Moving only the application clock dissociates them and is the
    // only way to reach the policy's expiry branch with the key still present.
    long pttlBefore = pttl(session.getId());
    clock.set(lastValidBefore.plus(SessionPolicy.IDLE_TIMEOUT).plusSeconds(1));

    MvcResult result = mockMvc.perform(authenticated(get(SESSION_PATH), cookie)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(result))
        .isEqualTo(
            "{\"authenticated\":false,"
                + "\"displayName\":null,"
                + "\"idleTimeout\":\"PT8H\","
                + "\"absoluteTimeout\":null,"
                + "\"lastValidActivityAt\":null}");

    assertThat(pttl(session.getId()))
        .as("an expired session must not be resurrected by the query")
        .isLessThanOrEqualTo(pttlBefore);
    assertThat(lastValidActivity(repository.findById(session.getId()))).isEqualTo(lastValidBefore);
  }

  /** Criterion 7. */
  @Test
  void onlyTheExactGetPairIsReachableAnonymously() throws Exception {
    assertThat(mockMvc.perform(get(SESSION_PATH)).andReturn().getResponse().getStatus())
        .isEqualTo(200);

    assertThat(mockMvc.perform(get(OTHER_API_PATH)).andReturn().getResponse().getStatus())
        .as("every other /api/v1/** path stays authenticated")
        .isBetween(300, 399);

    assertThat(mockMvc.perform(post(SESSION_PATH)).andReturn().getResponse().getStatus())
        .as("permitAll covers the GET method only")
        .isNotEqualTo(200);
  }

  private SelectiveTouchSessionRepository.SelectiveSession createLifecycleSession() {
    SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
    Instant initializedAt = clock.instant();
    session.setAttribute(
        SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
        initializedAt.getEpochSecond());
    session.setAttribute(
        SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE, initializedAt.getNano());
    session.setAttribute(SelectiveTouchSessionRepository.AUTHENTICATION_ACR_ATTRIBUTE, ACR);
    session.setAttribute(SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE, 0);
    session.setAttribute(
        SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, initializedAt);
    repository.save(session);
    return session;
  }

  private MockHttpServletRequestBuilder authenticated(
      MockHttpServletRequestBuilder request, Cookie cookie) {
    return request.cookie(cookie).with(oidcPrincipal());
  }

  private RequestPostProcessor oidcPrincipal() {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
    assertThat(registration).isNotNull();
    return oidcLogin()
        .clientRegistration(registration)
        .idToken(
            token ->
                token
                    .claim(IdTokenClaimNames.ISS, ISSUER)
                    .claim(IdTokenClaimNames.SUB, SUBJECT)
                    .claim("preferred_username", DISPLAY_NAME)
                    .claim("acr", ACR)
                    .claim("amr", List.of("pwd", "otp")));
  }

  private static String body(MvcResult result) throws Exception {
    result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private long sessionKeyCount() {
    Set<String> keys = redis.keys(SESSION_KEY_PREFIX + "*");
    return keys == null ? 0 : keys.size();
  }

  private void shortenTtl(String sessionId) {
    assertThat(redis.expire(sessionKey(sessionId), SHORT_TEST_TTL)).isTrue();
  }

  private long pttl(String sessionId) {
    Long value = redis.getExpire(sessionKey(sessionId), java.util.concurrent.TimeUnit.MILLISECONDS);
    assertThat(value).as("Redis PTTL for session %s", sessionId).isNotNull();
    return value;
  }

  private static String sessionKey(String sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }

  private static Cookie sessionCookie(String sessionId) {
    String encoded = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    return new Cookie(SESSION_COOKIE_NAME, encoded);
  }

  private static Instant lastValidActivity(
      SelectiveTouchSessionRepository.SelectiveSession session) {
    Long seconds =
        session.getAttribute(SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE);
    Integer nanos = session.getAttribute(SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE);
    assertThat(seconds).isNotNull();
    assertThat(nanos).isNotNull();
    return Instant.ofEpochSecond(seconds, nanos.longValue());
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
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-session-controller-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri(ISSUER + "/authorize")
              .tokenUri(ISSUER + "/token")
              .jwkSetUri(ISSUER + "/jwks")
              .issuerUri(ISSUER)
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Session Controller IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }

  /**
   * Local copy. {@code RedisSessionIT.MutableTestClock} has private constructors and lives in a
   * different nest, so it cannot be reused from here without editing a file outside this task's
   * allowlist.
   */
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
