package br.com.betufu.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import jakarta.servlet.Filter;
import java.time.Instant;
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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-authoritative-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused-secret",
      "BETUFU_S3_BUCKET=betufu-private",
      "BETUFU_S3_REGION=us-east-1",
      "BETUFU_S3_ACCESS_KEY_ID=test-access-key",
      "BETUFU_S3_SECRET_ACCESS_KEY=test-secret-key"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AuthoritativeStorageIT.TestOidcConfiguration.class)
class AuthoritativeStorageIT {

  public static final ArchRule NO_PLATFORM_ACCESS_TO_IDENTITY_INTERNAL =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("br.com.betufu.platform..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("br.com.betufu.identity.internal..");

  public static final ArchRule NO_DOMAIN_REPOSITORIES_UNDER_REDIS =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAnyPackage(
              "br.com.betufu.identity..",
              "br.com.betufu.verification..",
              "br.com.betufu.sports..",
              "br.com.betufu.odds..",
              "br.com.betufu.betting..",
              "br.com.betufu.ledger..",
              "br.com.betufu.settlement..",
              "br.com.betufu.ranking..",
              "br.com.betufu.chat..",
              "br.com.betufu.audit..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.data.redis.repository..");

  private static final String CURRENT_IDENTITY_PATH = "/api/v1/tracer/identity/current";

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final String REDIS_IMAGE_REFERENCE =
      "docker.io/library/redis:8.10.0-trixie@sha256:"
          + "344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  private static final DockerImageName REDIS_IMAGE =
      DockerImageName.parse(REDIS_IMAGE_REFERENCE).asCompatibleSubstituteFor("redis");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
    registry.add("BETUFU_REDIS_PORT", () -> String.valueOf(REDIS.getFirstMappedPort()));
    registry.add("BETUFU_REDIS_PASSWORD", () -> "");
  }

  @Autowired private PostgresAuthorityProbe postgresAuthorityProbe;

  @Autowired private RedisAuxiliaryProbe redisAuxiliaryProbe;

  @Autowired private PrivateObjectStorageProbe privateObjectStorageProbe;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @Autowired private WebApplicationContext applicationContext;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  @DisplayName(
      "PostgresAuthorityProbe valida que PostgreSQL 18 é a fonte autoritativa e possui V2 platform_guard")
  void postgresAuthorityProbeConfirmsPostgres18AndPlatformGuard() {
    PostgresAuthorityProbe.PostgresAuthorityStatus status = postgresAuthorityProbe.checkAuthority();

    assertThat(status.authoritative()).isTrue();
    assertThat(status.majorVersion()).isEqualTo(18);
    assertThat(status.guardRecordsCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName(
      "RedisAuxiliaryProbe e PrivateObjectStorageProbe confirmam limites auxiliares e bucket privado")
  void auxiliaryProbesConfirmDelimitedRoles() {
    RedisAuxiliaryProbe.RedisAuxiliaryStatus redisStatus =
        redisAuxiliaryProbe.checkAuxiliaryStatus();
    assertThat(redisStatus.auxiliaryHealthy()).isTrue();
    assertThat(redisStatus.pingStatus()).isEqualToIgnoringCase("PONG");

    PrivateObjectStorageProbe.PrivateObjectStorageStatus s3Status =
        privateObjectStorageProbe.checkStorageStatus();
    assertThat(s3Status.configured()).isTrue();
    assertThat(s3Status.bucketName()).isEqualTo("betufu-private");
    assertThat(s3Status.privateAccessEnforced()).isTrue();
  }

  @Test
  @DisplayName("Limpar ou reiniciar Redis não afeta a persistência nem altera dados no PostgreSQL")
  void flushingRedisDoesNotAffectPostgresData() throws Exception {
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "auth-storage-subject-" + UUID.randomUUID();

    // 1. Cria identidade no PostgreSQL via endpoint HTTP
    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(oidcPrincipal(issuer, subject, "storage@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PERSISTED"));

    // 2. Limpa completamente o Redis (FLUSHALL)
    redisConnectionFactory.getConnection().serverCommands().flushAll();

    // 3. Comprova que a identidade no PostgreSQL permanece 100% intacta
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.local_identity WHERE issuer = ? AND subject = ?",
            Integer.class,
            issuer,
            subject);
    assertThat(count).isEqualTo(1);

    // 4. Executa nova requisição HTTP: o serviço lê do PostgreSQL autoritativo sem falha
    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(oidcPrincipal(issuer, subject, "storage@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PERSISTED"));
  }

  @Test
  @DisplayName(
      "StorageAuthorityRules bloqueia acesso de platform a pacotes internos e repositórios de domínio sob Redis")
  void verifiesStorageAuthorityArchUnitRules() {
    JavaClasses productionClasses =
        new ClassFileImporter()
            .withImportOption(
                new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
            .importPackages("br.com.betufu");

    NO_PLATFORM_ACCESS_TO_IDENTITY_INTERNAL.check(productionClasses);
    NO_DOMAIN_REPOSITORIES_UNDER_REDIS.check(productionClasses);

    // Prova de discriminação da regra de isolamento
    JavaClasses violatorClasses =
        new ClassFileImporter().importClasses(SyntheticPlatformViolator.class);
    assertThatThrownBy(() -> NO_PLATFORM_ACCESS_TO_IDENTITY_INTERNAL.check(violatorClasses))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("was violated");
  }

  private RequestPostProcessor oidcPrincipal(String issuer, String subject, String email) {
    Instant now = Instant.now();
    OidcIdToken token =
        OidcIdToken.withTokenValue("storage-it-" + UUID.randomUUID())
            .issuedAt(now.minusSeconds(60))
            .expiresAt(now.plusSeconds(300))
            .claim(IdTokenClaimNames.ISS, issuer)
            .claim(IdTokenClaimNames.SUB, subject)
            .claim("email", email)
            .build();

    OidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("OIDC_USER"), token, IdTokenClaimNames.SUB);

    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
    assertThat(registration).isNotNull();

    return oidcLogin().oidcUser(oidcUser).clientRegistration(registration);
  }

  /**
   * Fixture sintética no pacote de platform tentando acessar identity.internal para discriminação
   * de regra.
   */
  static class SyntheticPlatformViolator {
    @SuppressWarnings("unused")
    private br.com.betufu.identity.internal.LocalIdentityService unauthorizedReference;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-authoritative-it")
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
              .clientName("BETUFU Authoritative IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
