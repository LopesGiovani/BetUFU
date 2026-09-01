package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.identity.internal.RoleGrantService;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-role-change-session-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(RoleChangeSessionTerminationIT.TestOidcConfiguration.class)
class RoleChangeSessionTerminationIT {

  private static final String PROBE_ISSUER = "https://idp.invalid";
  private static final String TARGET_SUBJECT_A = "role-change-target-sub-alpha";
  private static final String ACTOR_SUBJECT_B = "role-change-actor-sub-beta";

  private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
  private static final String EXPIRES_KEY_PREFIX = "betufu:session:sessions:expires:";
  private static final String REVERSE_PRINCIPAL_KEY_PREFIX = "betufu:session:sessions:principal:";
  private static final String PRINCIPAL_INDEX_PREFIX =
      "betufu:session:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:";

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private ApplicationContext applicationContext;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private StringRedisTemplate redisTemplate;

  private SelectiveTouchSessionRepository sessionRepository;

  @Autowired private RoleGrantService roleGrantService;

  @Autowired private AuditEventRepository auditEventRepository;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    var connection = redisTemplate.getConnectionFactory().getConnection();
    connection.serverCommands().flushDb();
    sessionRepository =
        (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
  }

  @Test
  @DisplayName(
      "Alteração de papel encerra todas as sessões do alvo após commit e preserva a sessão do ator (D-49)")
  void roleChangeTerminatesTargetSessionsPostCommitAndPreservesActorSession() {
    // 1. Setup target identity and actor identity in database
    UUID targetId = createIdentity(PROBE_ISSUER, TARGET_SUBJECT_A);
    UUID actorId = createIdentity(PROBE_ISSUER, ACTOR_SUBJECT_B);

    // 2. Create 2 authenticated sessions in Redis for Target A
    SelectiveTouchSessionRepository.SelectiveSession targetSession1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(targetSession1);

    SelectiveTouchSessionRepository.SelectiveSession targetSession2 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(targetSession2);

    // 3. Create 1 authenticated session in Redis for Actor B
    SelectiveTouchSessionRepository.SelectiveSession actorSession1 =
        createAuthenticatedSession(ACTOR_SUBJECT_B);
    sessionRepository.save(actorSession1);

    // Pre-assertion: All 3 sessions exist in Redis
    assertThat(sessionRepository.findById(targetSession1.getId())).isNotNull();
    assertThat(sessionRepository.findById(targetSession2.getId())).isNotNull();
    assertThat(sessionRepository.findById(actorSession1.getId())).isNotNull();

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(2);
    assertThat(sessionRepository.findByPrincipalName(ACTOR_SUBJECT_B)).hasSize(1);

    // 3b. Semeia ADMINISTRADOR_GERAL para o ator, direto no banco.
    //     A concessao pelo servico exigiria que ele ja fosse administrador geral — e o
    //     problema de bootstrap que o Plano 02-08 resolveu por semente de ambiente.
    jdbcTemplate.update(
        "INSERT INTO public.operational_role_grant"
            + " (grant_id, identity_id, role_name, scope_ref, status, granted_at)"
            + " VALUES (gen_random_uuid(), ?, ?, NULL, ?, CURRENT_TIMESTAMP)",
        actorId,
        OperationalRole.ADMINISTRADOR_GERAL.name(),
        "VIGENTE");

    // 4. Actor B grants role MODERADOR to Target A (triggers transaction commit and post-commit
    // listener)
    roleGrantService.grantRole(
        actorId,
        targetId,
        OperationalRole.MODERADOR,
        java.util.UUID.fromString("11111111-2222-4333-8444-555555555555"));

    // 5. Post-commit verification: Target A's 2 sessions are TERMINATED (dead in Redis)
    assertThat(sessionRepository.findById(targetSession1.getId()))
        .as("Target session 1 must be deleted")
        .isNull();
    assertThat(sessionRepository.findById(targetSession2.getId()))
        .as("Target session 2 must be deleted")
        .isNull();
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A))
        .as("Target A must have 0 active sessions")
        .isEmpty();

    assertThat(redisTemplate.hasKey(SESSION_KEY_PREFIX + targetSession1.getId())).isFalse();
    assertThat(redisTemplate.hasKey(SESSION_KEY_PREFIX + targetSession2.getId())).isFalse();

    // 6. Post-commit verification: Actor B's session SURVIVES INTACT in Redis
    assertThat(sessionRepository.findById(actorSession1.getId()))
        .as("Actor B session must survive intact")
        .isNotNull();
    assertThat(sessionRepository.findByPrincipalName(ACTOR_SUBJECT_B))
        .as("Actor B must still have 1 active session")
        .hasSize(1);
    assertThat(redisTemplate.hasKey(SESSION_KEY_PREFIX + actorSession1.getId())).isTrue();

    // 7. Verification: Audit trail has the role grant event
    List<AuditEvent> auditEvents = auditEventRepository.findAll();
    assertThat(auditEvents)
        .anySatisfy(
            event -> {
              assertThat(event.getActorIdentityId()).isEqualTo(actorId);
              assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
              assertThat(event.getAction()).isEqualTo("PAPEL_CONCEDIDO");
              assertThat(event.getOutcome()).isEqualTo("PERMITIDO");
              assertThat(event.getNewValue())
                  .as("a trilha registra o papel com o escopo da concessao")
                  .isEqualTo("MODERADOR;scope=11111111-2222-4333-8444-555555555555");
            });
  }

  private UUID createIdentity(String issuer, String subject) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
        issuer,
        subject);
    return jdbcTemplate.queryForObject(
        "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
        UUID.class,
        issuer,
        subject);
  }

  private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(
      String subject) {
    SelectiveTouchSessionRepository.SelectiveSession session = sessionRepository.createSession();
    Instant now = Instant.now();

    OidcIdToken idToken =
        new OidcIdToken(
            "token-value-" + UUID.randomUUID(),
            now,
            now.plusSeconds(3600),
            Map.of(
                IdTokenClaimNames.ISS, PROBE_ISSUER,
                IdTokenClaimNames.SUB, subject));

    DefaultOidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, IdTokenClaimNames.SUB);

    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(
        new UsernamePasswordAuthenticationToken(oidcUser, "n/a", oidcUser.getAuthorities()));

    session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
    session.setLastAccessedTime(now);
    return session;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-role-change-session-it")
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
              .clientName("BETUFU Role Change Session IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
