package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.betufu.platform.security.OidcRevocationClient;
import br.com.betufu.platform.security.SecurityConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves identity session enumeration and termination against a real Redis instance.
 *
 * <p>Requirements verified:
 *
 * <ul>
 *   <li>The session repository bean exposes {@link FindByIndexNameSessionRepository};
 *   <li>Two sessions belonging to target identity A and one session belonging to third-party
 *       identity B are persisted;
 *   <li>Terminating identity A deletes exactly the two sessions of A while identity B's session
 *       survives intact in Redis.
 * </ul>
 */
@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      SessionSecurityConfiguration.class,
      IdentitySessionTerminationService.class,
      SessionInvalidationListener.class,
      OidcSessionRevocationService.class,
      IdentitySessionTerminationIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
      "BETUFU_DB_JDBC_URL=jdbc:postgresql://database.invalid/betufu",
      "BETUFU_DB_USER=unused",
      "BETUFU_DB_PASSWORD=unused",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-identity-termination-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
class IdentitySessionTerminationIT {

  private static final String TARGET_SUBJECT_A = "identity-target-sub-alpha";
  private static final String THIRD_PARTY_SUBJECT_B = "identity-third-party-sub-beta";
  private static final String PROBE_ISSUER = "https://idp.invalid";
  private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
  private static final String EXPIRES_KEY_PREFIX = "betufu:session:sessions:expires:";
  private static final String PRINCIPAL_KEY_PREFIX = "betufu:session:sessions:principal:";
  private static final String INDEX_KEY_PREFIX = "betufu:session:index:";

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private StringRedisTemplate redis;

  @Autowired private MutableTestClock clock;

  @Autowired private IdentitySessionTerminationService terminationService;

  private SelectiveTouchSessionRepository repository;
  private RedisOperations<String, Object> sessionRedis;

  @BeforeEach
  void prepareRedis() {
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
              return null;
            });
    clock.set(Instant.now());
    repository = (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
    sessionRedis = repository.getSessionRedisOperations();
  }

  @Test
  @DisplayName(
      "Session repository bean implements FindByIndexNameSessionRepository and is usable for principal lookup")
  void sessionRepositoryExposesFindByIndexNameCapability() {
    Object bean = applicationContext.getBean("sessionRepository");
    assertThat(bean).isInstanceOf(FindByIndexNameSessionRepository.class);

    SelectiveTouchSessionRepository.SelectiveSession session =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    repository.save(session);

    @SuppressWarnings("unchecked")
    FindByIndexNameSessionRepository<SelectiveTouchSessionRepository.SelectiveSession> indexedRepo =
        (FindByIndexNameSessionRepository<SelectiveTouchSessionRepository.SelectiveSession>) bean;

    var found = indexedRepo.findByPrincipalName(TARGET_SUBJECT_A);
    assertThat(found).containsKey(session.getId());
  }

  @Test
  @DisplayName(
      "Terminating target identity terminates all its sessions and leaves other identity's session intact")
  void terminateAllSessionsForTargetIdentityLeavesOtherIdentityIntact() {
    // 1. Create two sessions for Target A
    SelectiveTouchSessionRepository.SelectiveSession sessionA1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    repository.save(sessionA1);

    SelectiveTouchSessionRepository.SelectiveSession sessionA2 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    repository.save(sessionA2);

    // 2. Create one session for Third-Party B
    SelectiveTouchSessionRepository.SelectiveSession sessionB1 =
        createAuthenticatedSession(THIRD_PARTY_SUBJECT_B);
    repository.save(sessionB1);

    // Pre-assertion: All 3 sessions exist in repository and Redis
    assertThat(repository.findById(sessionA1.getId())).isNotNull();
    assertThat(repository.findById(sessionA2.getId())).isNotNull();
    assertThat(repository.findById(sessionB1.getId())).isNotNull();

    assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(2);
    assertThat(repository.findByPrincipalName(THIRD_PARTY_SUBJECT_B)).hasSize(1);

    assertThat(redis.hasKey(sessionKey(sessionA1.getId()))).isTrue();
    assertThat(redis.hasKey(expiredSessionKey(sessionA1.getId()))).isTrue();
    assertThat(redis.hasKey(reversePrincipalKey(sessionA1.getId()))).isTrue();

    assertThat(redis.hasKey(sessionKey(sessionA2.getId()))).isTrue();
    assertThat(redis.hasKey(expiredSessionKey(sessionA2.getId()))).isTrue();
    assertThat(redis.hasKey(reversePrincipalKey(sessionA2.getId()))).isTrue();

    assertThat(redis.hasKey(sessionKey(sessionB1.getId()))).isTrue();
    assertThat(redis.hasKey(expiredSessionKey(sessionB1.getId()))).isTrue();
    assertThat(redis.hasKey(reversePrincipalKey(sessionB1.getId()))).isTrue();

    assertThat(sessionRedis.opsForSet().size(principalIndexKey(TARGET_SUBJECT_A))).isEqualTo(2L);
    assertThat(sessionRedis.opsForSet().size(principalIndexKey(THIRD_PARTY_SUBJECT_B)))
        .isEqualTo(1L);

    // 3. Execute termination for Target A
    IdentitySessionTerminationService.TerminationSummary summary =
        terminationService.terminateAllSessionsFor(
            TARGET_SUBJECT_A, SessionTerminationReason.ADMINISTRATIVE);

    assertThat(summary.totalTerminated()).isEqualTo(2);

    // 4. Assert: Sessions and auxiliary keys of Target A are dead in Redis
    assertThat(repository.findById(sessionA1.getId())).as("Session A1 must be deleted").isNull();
    assertThat(repository.findById(sessionA2.getId())).as("Session A2 must be deleted").isNull();
    assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A))
        .as("Target A should have 0 active sessions")
        .isEmpty();

    assertThat(redis.hasKey(sessionKey(sessionA1.getId()))).isFalse();
    assertThat(redis.hasKey(expiredSessionKey(sessionA1.getId()))).isFalse();
    assertThat(redis.hasKey(reversePrincipalKey(sessionA1.getId()))).isFalse();

    assertThat(redis.hasKey(sessionKey(sessionA2.getId()))).isFalse();
    assertThat(redis.hasKey(expiredSessionKey(sessionA2.getId()))).isFalse();
    assertThat(redis.hasKey(reversePrincipalKey(sessionA2.getId()))).isFalse();

    boolean targetIndexCleaned =
        Boolean.FALSE.equals(redis.hasKey(principalIndexKey(TARGET_SUBJECT_A)))
            || Long.valueOf(0L)
                .equals(sessionRedis.opsForSet().size(principalIndexKey(TARGET_SUBJECT_A)));
    assertThat(targetIndexCleaned).isTrue();

    // 5. Assert: Session and auxiliary keys of Third-Party B survive intact!
    SelectiveTouchSessionRepository.SelectiveSession survivingSessionB =
        repository.findById(sessionB1.getId());
    assertThat(survivingSessionB).as("Third-party B session must survive intact").isNotNull();
    assertThat(repository.findByPrincipalName(THIRD_PARTY_SUBJECT_B))
        .as("Third-party B must still have its session indexed")
        .containsKey(sessionB1.getId());

    assertThat(redis.hasKey(sessionKey(sessionB1.getId()))).isTrue();
    assertThat(redis.hasKey(expiredSessionKey(sessionB1.getId()))).isTrue();
    assertThat(redis.hasKey(reversePrincipalKey(sessionB1.getId()))).isTrue();
    assertThat(redis.hasKey(principalIndexKey(THIRD_PARTY_SUBJECT_B))).isTrue();
    assertThat(sessionRedis.opsForSet().size(principalIndexKey(THIRD_PARTY_SUBJECT_B)))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("Terminating an identity without active sessions is a valid no-op")
  void terminateIdentityWithoutSessionsIsNoOp() {
    IdentitySessionTerminationService.TerminationSummary summary =
        terminationService.terminateAllSessionsFor(
            "non-existent-user-sub", SessionTerminationReason.SECURITY);

    assertThat(summary.totalTerminated()).isZero();
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.isFullyRevoked()).isTrue();
  }

  @Test
  @DisplayName(
      "Orphaned index member without session hash is reconciled and removed during lookup while live session in same index survives")
  void orphanedIndexMemberIsReconciledDuringLookupWhileLiveSessionSurvives() {
    // 1. Create and save a live session for Target A
    SelectiveTouchSessionRepository.SelectiveSession liveSession =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    repository.save(liveSession);
    String liveSessionId = liveSession.getId();

    // 2. Inject an orphaned session ID directly into the Redis index set for Target A
    // using the repository's serializer (simulating missing keyspace notification)
    String orphanSessionId = "orphaned-dead-session-id-123";
    String indexKey = principalIndexKey(TARGET_SUBJECT_A);
    sessionRedis.opsForSet().add(indexKey, orphanSessionId);

    // Preconditions:
    assertThat(sessionRedis.opsForSet().size(indexKey)).isEqualTo(2L);
    assertThat(redis.hasKey(sessionKey(orphanSessionId))).isFalse();
    assertThat(redis.hasKey(expiredSessionKey(orphanSessionId))).isFalse();
    assertThat(redis.hasKey(reversePrincipalKey(orphanSessionId))).isFalse();

    // 3. Lookup by principal name triggers safe self-healing
    var found = repository.findByPrincipalName(TARGET_SUBJECT_A);

    // Assert: Only the live session is returned
    assertThat(found).hasSize(1);
    assertThat(found).containsKey(liveSessionId);
    assertThat(found).doesNotContainKey(orphanSessionId);

    // Assert: Redis index set for Target A now has only the live session (orphan member was purged)
    assertThat(sessionRedis.opsForSet().size(indexKey)).isEqualTo(1L);
    assertThat(sessionRedis.opsForSet().isMember(indexKey, liveSessionId)).isTrue();
    assertThat(sessionRedis.opsForSet().isMember(indexKey, orphanSessionId)).isFalse();

    // 4. Assert: Live session remains fully intact in Redis
    assertThat(redis.hasKey(sessionKey(liveSessionId))).isTrue();
    assertThat(redis.hasKey(expiredSessionKey(liveSessionId))).isTrue();
    assertThat(redis.hasKey(reversePrincipalKey(liveSessionId))).isTrue();
  }

  @Test
  @DisplayName(
      "Periodic SCAN reconciliation purges orphaned index members across all indexes without touching live sessions")
  void periodicScanReconciliationPurgesOrphanedIndexMembers() {
    // 1. Create a live session for Target A
    SelectiveTouchSessionRepository.SelectiveSession liveSessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    repository.save(liveSessionA);

    // 2. Create a live session for Third Party B
    SelectiveTouchSessionRepository.SelectiveSession liveSessionB =
        createAuthenticatedSession(THIRD_PARTY_SUBJECT_B);
    repository.save(liveSessionB);

    // 3. Inject orphaned session IDs into both indexes using the repository serializer
    String orphanA = "orphan-target-a-999";
    String orphanB = "orphan-third-party-b-888";
    sessionRedis.opsForSet().add(principalIndexKey(TARGET_SUBJECT_A), orphanA);
    sessionRedis.opsForSet().add(principalIndexKey(THIRD_PARTY_SUBJECT_B), orphanB);

    assertThat(sessionRedis.opsForSet().size(principalIndexKey(TARGET_SUBJECT_A))).isEqualTo(2L);
    assertThat(sessionRedis.opsForSet().size(principalIndexKey(THIRD_PARTY_SUBJECT_B)))
        .isEqualTo(2L);

    // 4. Execute explicit SCAN reconciliation
    repository.reconcileOrphanedIndexes();

    // 5. Verify orphan members are purged and live sessions survive
    assertThat(sessionRedis.opsForSet().size(principalIndexKey(TARGET_SUBJECT_A))).isEqualTo(1L);
    assertThat(
            sessionRedis
                .opsForSet()
                .isMember(principalIndexKey(TARGET_SUBJECT_A), liveSessionA.getId()))
        .isTrue();
    assertThat(sessionRedis.opsForSet().isMember(principalIndexKey(TARGET_SUBJECT_A), orphanA))
        .isFalse();

    assertThat(sessionRedis.opsForSet().size(principalIndexKey(THIRD_PARTY_SUBJECT_B)))
        .isEqualTo(1L);
    assertThat(
            sessionRedis
                .opsForSet()
                .isMember(principalIndexKey(THIRD_PARTY_SUBJECT_B), liveSessionB.getId()))
        .isTrue();
    assertThat(sessionRedis.opsForSet().isMember(principalIndexKey(THIRD_PARTY_SUBJECT_B), orphanB))
        .isFalse();

    assertThat(redis.hasKey(sessionKey(liveSessionA.getId()))).isTrue();
    assertThat(redis.hasKey(sessionKey(liveSessionB.getId()))).isTrue();
  }

  private static String sessionKey(String sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }

  private static String expiredSessionKey(String sessionId) {
    return EXPIRES_KEY_PREFIX + sessionId;
  }

  private static String reversePrincipalKey(String sessionId) {
    return PRINCIPAL_KEY_PREFIX + sessionId;
  }

  private static String principalIndexKey(String principalName) {
    return INDEX_KEY_PREFIX
        + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
        + ":"
        + principalName;
  }

  private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(
      String subject) {
    SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
    Instant now = clock.instant();

    OidcIdToken idToken =
        OidcIdToken.withTokenValue("probe-token-value-" + subject)
            .issuer(PROBE_ISSUER)
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
    session.setAttribute(
        SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE, now.getEpochSecond());
    session.setAttribute(SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE, now.getNano());
    session.setAttribute(SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, now);

    return session;
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
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-identity-termination-it")
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
              .clientName("BETUFU Identity Termination IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }

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
