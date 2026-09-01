package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.verification.internal.EnrollmentService;
import br.com.betufu.verification.internal.MatriculaHmac;
import br.com.betufu.verification.internal.MatriculaNormalizer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-fingerprint-compat-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(EnrollmentFingerprintCompatibilityIT.TestOidcConfiguration.class)
class EnrollmentFingerprintCompatibilityIT {

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:"
                  + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

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

  @Autowired private EnrollmentFingerprintPort fingerprintPort;
  @Autowired private MatriculaHmac matriculaHmac;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.sports_athlete, public.sports_team, public.sports_organization, "
            + "public.ufu_enrollment, public.local_identity CASCADE");
  }

  @Test
  @DisplayName(
      "EnrollmentFingerprintPort produz digests e versões byte-compatíveis com o HMAC da Fase 2")
  void producesByteIdenticalFingerprintsToPhase2() {
    String[] testCases = {
      "12345ABC", "  12345abc  ", "123-45.a/b c", "20261BSU001", "ABCDE12345FGHIJ67890"
    };

    for (String raw : testCases) {
      String normalized = MatriculaNormalizer.normalize(raw);
      byte[] phase2Digest = matriculaHmac.calculateCurrent(normalized);
      short phase2Version = EnrollmentService.parseHmacVersion(matriculaHmac.currentVersion());

      Optional<EnrollmentFingerprintPort.Fingerprint> fingerprintOpt =
          fingerprintPort.computeFingerprint(raw);
      assertThat(fingerprintOpt).isPresent();

      EnrollmentFingerprintPort.Fingerprint fingerprint = fingerprintOpt.get();
      assertThat(fingerprint.hash())
          .as("Hash para entrada '%s' deve ser byte-compatível com a Fase 2", raw)
          .isEqualTo(phase2Digest);
      assertThat(fingerprint.version())
          .as("Versão para entrada '%s' deve coincidir com a versão da Fase 2", raw)
          .isEqualTo(phase2Version);
    }
  }

  @Test
  @DisplayName(
      "Entradas inválidas lançam IllegalArgumentException e vazias retornam Optional.empty")
  void handlesInvalidAndBlankInputsSafely() {
    assertThat(fingerprintPort.computeFingerprint(null)).isEmpty();
    assertThat(fingerprintPort.computeFingerprint("")).isEmpty();
    assertThat(fingerprintPort.computeFingerprint("   ")).isEmpty();

    assertThatThrownBy(() -> fingerprintPort.computeFingerprint("123"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> fingerprintPort.computeFingerprint("@invalid@"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> fingerprintPort.computeFingerprint("123456789012345678901"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Verifica persistência cruzada entre ufu_enrollment e sports_athlete com mesmo HMAC")
  void crossVerifiesPersistenceMatch() {
    String matricula = "20261ENG042";
    String normalized = MatriculaNormalizer.normalize(matricula);
    byte[] expectedHash = matriculaHmac.calculateCurrent(normalized);

    // 1. Persistência de matrícula na conta (Fase 2)
    UUID identityId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', 'student-042')",
        identityId);
    enrollmentService.register(identityId, matricula);

    byte[] enrollmentHmac =
        jdbcTemplate.queryForObject(
            "SELECT matricula_hmac FROM public.ufu_enrollment WHERE identity_id = ?",
            byte[].class,
            identityId);

    // 2. Persistência de atleta com a mesma matrícula (Fase 3)
    UUID athleteId = UUID.randomUUID();
    EnrollmentFingerprintPort.Fingerprint fingerprint =
        fingerprintPort.computeFingerprint(matricula).orElseThrow();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version) VALUES (?, ?, ?, ?, ?)",
        athleteId,
        "Atleta 042",
        "atleta-042",
        fingerprint.hash(),
        fingerprint.version());

    byte[] athleteHmac =
        jdbcTemplate.queryForObject(
            "SELECT matricula_hmac FROM public.sports_athlete WHERE athlete_id = ?",
            byte[].class,
            athleteId);

    assertThat(enrollmentHmac).isNotNull().isEqualTo(expectedHash);
    assertThat(athleteHmac).isNotNull().isEqualTo(expectedHash);
    assertThat(athleteHmac).isEqualTo(enrollmentHmac);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-fingerprint-compat-it")
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
              .clientName("BETUFU Fingerprint Compatibility Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
