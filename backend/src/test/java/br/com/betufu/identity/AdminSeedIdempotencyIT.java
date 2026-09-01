package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.identity.internal.AdminSeedInitializer;
import br.com.betufu.identity.internal.OperationalRoleGrant;
import br.com.betufu.identity.internal.OperationalRoleGrantRepository;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.session.RedisContainerSupport;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-admin-seed-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AdminSeedIdempotencyIT.TestOidcConfiguration.class)
class AdminSeedIdempotencyIT {

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

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private CorrelationIdFilter correlationIdFilter;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  @Autowired private AdminSeedInitializer adminSeedInitializer;

  @Autowired private OperationalRoleGrantRepository roleGrantRepository;

  @Autowired private AuditEventRepository auditEventRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    var connection = redisTemplate.getConnectionFactory().getConnection();
    connection.serverCommands().flushDb();

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();
  }

  @Test
  @DisplayName(
      "D-47: Semente concede papel ADMINISTRADOR_GERAL para conta existente e registra auditoria")
  void seedsAdminRoleForExistingIdentityAndRecordsAudit() {
    UUID identityId = createIdentity("https://idp.invalid", "seed-admin-sub", "seed_admin");

    boolean result = adminSeedInitializer.initializeSeedWithIdentifier(identityId.toString());
    assertThat(result).isTrue();

    List<OperationalRoleGrant> grants = roleGrantRepository.findActiveByIdentityId(identityId);
    assertThat(grants).hasSize(1);
    OperationalRoleGrant grant = grants.getFirst();
    assertThat(grant.getRoleName()).isEqualTo(OperationalRole.ADMINISTRADOR_GERAL);
    assertThat(grant.getScopeRef()).isNull();
    assertThat(grant.getStatus()).isEqualTo(OperationalRoleGrant.STATUS_VIGENTE);

    List<AuditEvent> auditEvents = auditEventRepository.findAll();
    assertThat(auditEvents).hasSize(1);
    AuditEvent event = auditEvents.getFirst();
    assertThat(event.getTargetIdentityId()).isEqualTo(identityId);
    assertThat(event.getAction()).isEqualTo("SEMENTE_ADMINISTRADOR_INICIAL");
    assertThat(event.getOutcome()).isEqualTo("PERMITIDO");
    assertThat(event.getNewValue()).isEqualTo("ADMINISTRADOR_GERAL");
  }

  @Test
  @DisplayName(
      "D-47: Semente repetida deixa 1 concessão, 1 auditoria e o mesmo carimbo de data/hora")
  void repeatedSeedIsIdempotentDoesNotDuplicateGrantOrAuditAndPreservesTimestamp() {
    UUID identityId = createIdentity("https://idp.invalid", "seed-repeat-sub", "repeat_admin");

    // Primeira execução
    boolean firstRun = adminSeedInitializer.initializeSeedWithIdentifier(identityId.toString());
    assertThat(firstRun).isTrue();

    Instant initialCreatedAt =
        jdbcTemplate.queryForObject(
            "SELECT granted_at FROM public.operational_role_grant WHERE identity_id = ?",
            Instant.class,
            identityId);
    assertThat(initialCreatedAt).isNotNull();

    // Segunda e terceira execuções
    boolean secondRun = adminSeedInitializer.initializeSeedWithIdentifier(identityId.toString());
    boolean thirdRun = adminSeedInitializer.initializeSeedWithIdentifier(identityId.toString());

    assertThat(secondRun).isFalse();
    assertThat(thirdRun).isFalse();

    // Exatamente 1 concessão
    Integer grantCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.operational_role_grant WHERE identity_id = ?",
            Integer.class,
            identityId);
    assertThat(grantCount).isEqualTo(1);

    // Carimbo de data/hora inalterado
    Instant finalCreatedAt =
        jdbcTemplate.queryForObject(
            "SELECT granted_at FROM public.operational_role_grant WHERE identity_id = ?",
            Instant.class,
            identityId);
    assertThat(finalCreatedAt).isEqualTo(initialCreatedAt);

    // Exatamente 1 evento de auditoria
    List<AuditEvent> auditEvents = auditEventRepository.findAll();
    assertThat(auditEvents).hasSize(1);
  }

  @Test
  @DisplayName("D-47: caminho real do ApplicationRunner reverte concessao quando a auditoria falha")
  void applicationRunnerRollsBackGrantWhenAuditFails() {
    UUID identityId = createIdentity("https://idp.invalid", "seed-runner-rollback", "runner_admin");
    AuditRecorder failingAudit =
        entry -> {
          throw new IllegalStateException("simulated audit failure");
        };
    AdminSeedInitializer runner =
        new AdminSeedInitializer(
            roleGrantRepository, failingAudit, identityId.toString(), transactionManager);

    assertThatThrownBy(() -> runner.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("simulated audit failure");

    assertThat(roleGrantRepository.findActiveByIdentityId(identityId)).isEmpty();
    assertThat(auditEventRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName(
      "D-47: Identidade inexistente fica pendente observável e NÃO cria cadastro nem concessão")
  void nonExistentIdentityStaysPendingWithoutCreatingRecordOrGrant() {
    UUID nonExistentId = UUID.randomUUID();

    boolean result = adminSeedInitializer.initializeSeedWithIdentifier(nonExistentId.toString());
    assertThat(result).isFalse();

    // Nenhum registro em local_identity
    Integer identityCount =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.local_identity", Integer.class);
    assertThat(identityCount).isZero();

    // Nenhuma concessão
    Integer grantCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.operational_role_grant", Integer.class);
    assertThat(grantCount).isZero();

    // Nenhuma auditoria
    assertThat(auditEventRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("D-47: Identificador malformado ou em branco não explode e permanece pendente")
  void malformedOrBlankIdentifierIsHandledSafely() {
    assertThat(adminSeedInitializer.initializeSeedWithIdentifier("not-a-valid-uuid")).isFalse();
    assertThat(adminSeedInitializer.initializeSeedWithIdentifier("")).isFalse();
    assertThat(adminSeedInitializer.initializeSeedWithIdentifier("   ")).isFalse();
    assertThat(adminSeedInitializer.initializeSeedWithIdentifier(null)).isFalse();

    // findActiveRolesForUpdate e SELECT ... FOR UPDATE e exige transacao ativa; a leitura
    // equivalente por jdbcTemplate prova a mesma propriedade sem acoplar a prova ao lock.
    Integer grantCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.operational_role_grant WHERE role_name = 'ADMINISTRADOR_GERAL' AND status = 'VIGENTE'",
            Integer.class);
    assertThat(grantCount).isZero();
    assertThat(auditEventRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Ausência material de endpoint privilegiado de bootstrap: nenhum mapping registrado")
  void noPrivilegedBootstrapEndpointExists() {
    // Ausencia se prova por inspecao do RequestMappingHandlerMapping, nunca por status HTTP.
    // Um 302 ou 401 diz apenas que a autenticacao foi exigida antes do handler: ele nao
    // distingue "negado" de "inexistente". O mesmo criterio ja governa /api/v1/diagnostic.
    // Por nome: existe mais de um bean deste tipo no contexto (o do MVC e o do Actuator),
    // e o do MVC e quem registra os controllers da aplicacao.
    RequestMappingHandlerMapping mapping =
        applicationContext.getBean(
            "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

    List<String> bootstrapMappings =
        mapping.getHandlerMethods().keySet().stream()
            .map(Object::toString)
            .filter(info -> info.toLowerCase(Locale.ROOT).contains("bootstrap"))
            .toList();

    assertThat(bootstrapMappings)
        .as("nenhum mapping de bootstrap pode existir em nenhum profile de runtime")
        .isEmpty();
  }

  private UUID createIdentity(String issuer, String subject, String nickname) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
        issuer,
        subject);
    UUID identityId =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            issuer,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?) ON CONFLICT (identity_id) DO NOTHING",
        identityId,
        nickname);
    return identityId;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-admin-seed-it")
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
              .clientName("BETUFU Admin Seed IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
