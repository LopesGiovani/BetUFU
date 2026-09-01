package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.betufu.verification.internal.UfuEnrollment;
import br.com.betufu.verification.internal.UfuEnrollmentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-uniqueness-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(UfuEnrollmentUniquenessIT.TestOidcConfiguration.class)
class UfuEnrollmentUniquenessIT {

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

  @Autowired private UfuEnrollmentRepository enrollmentRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.ufu_enrollment, public.audit_event, public.betufu_profile, public.local_identity CASCADE");
  }

  @Test
  @DisplayName(
      "Aplica migration V5 e valida colunas, tipos, ausência de PII/expiração e constraints no PostgreSQL 18")
  void appliesMigrationV5AndVerifiesTableAndCatalogContract() {
    List<String> columns =
        jdbcTemplate.queryForList(
            """
                SELECT attribute.attname
                FROM pg_catalog.pg_attribute attribute
                JOIN pg_catalog.pg_class relation
                  ON relation.oid = attribute.attrelid
                JOIN pg_catalog.pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = 'ufu_enrollment'
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                ORDER BY attribute.attnum
                """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "enrollment_id",
            "identity_id",
            "matricula_hmac",
            "hmac_version",
            "status",
            "created_at",
            "updated_at");

    // VERI-06 e D-26: Prova estrutural de ausência de colunas com número cru ou expiração
    for (String col : columns) {
      String lower = col.toLowerCase();
      assertThat(lower).doesNotContain("numero").doesNotContain("validade").doesNotContain("expir");
    }

    // Validação de tipos exatos no catálogo
    String hmacType =
        jdbcTemplate.queryForObject(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'ufu_enrollment'
                  AND column_name = 'matricula_hmac'
                """,
            String.class);
    assertThat(hmacType).isEqualTo("bytea");

    String versionType =
        jdbcTemplate.queryForObject(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'ufu_enrollment'
                  AND column_name = 'hmac_version'
                """,
            String.class);
    assertThat(versionType).isEqualTo("smallint");

    List<String> constraints =
        jdbcTemplate.queryForList(
            """
                SELECT constraint_record.conname
                FROM pg_catalog.pg_constraint constraint_record
                WHERE constraint_record.conrelid = 'public.ufu_enrollment'::regclass
                  AND constraint_record.contype IN ('p', 'u', 'c', 'f')
                ORDER BY constraint_record.conname
                """,
            String.class);

    assertThat(constraints)
        .contains(
            "pk_ufu_enrollment",
            "fk_ufu_enrollment_identity",
            "ck_ufu_enrollment_hmac_octets",
            "ck_ufu_enrollment_hmac_version_range",
            "ck_ufu_enrollment_status_not_empty",
            "ck_ufu_enrollment_status_octets",
            "ck_ufu_enrollment_status_values",
            "ck_ufu_enrollment_timestamp_order");

    // Validação dos dois índices únicos parciais
    List<String> indexDefs =
        jdbcTemplate.queryForList(
            """
                SELECT indexdef
                FROM pg_catalog.pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'ufu_enrollment'
                ORDER BY indexname
                """,
            String.class);

    assertThat(indexDefs)
        .anySatisfy(
            def -> {
              assertThat(def)
                  .contains("uq_ufu_enrollment_active_hmac")
                  .contains("UNIQUE INDEX")
                  .contains("matricula_hmac")
                  .contains("WHERE");
            });

    assertThat(indexDefs)
        .anySatisfy(
            def -> {
              assertThat(def)
                  .contains("uq_ufu_enrollment_active_identity")
                  .contains("UNIQUE INDEX")
                  .contains("identity_id")
                  .contains("WHERE");
            });
  }

  @Test
  @DisplayName(
      "Constraint ck_ufu_enrollment_hmac_octets rejeita digest com tamanho diferente de 32 octetos")
  void checkConstraintRejectsMatriculaHmacNotExactly32Bytes() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-hmac-octets");

    // 0 octetos
    assertConstraintViolation(
        identity, new byte[0], (short) 1, "ATIVO", "ck_ufu_enrollment_hmac_octets");
    // 16 octetos
    assertConstraintViolation(
        identity, new byte[16], (short) 1, "ATIVO", "ck_ufu_enrollment_hmac_octets");
    // 33 octetos
    assertConstraintViolation(
        identity, new byte[33], (short) 1, "ATIVO", "ck_ufu_enrollment_hmac_octets");

    // 32 octetos exatos são aceitos
    byte[] validDigest = createSampleDigest((byte) 0xAA);
    int inserted = insertEnrollmentDirect(identity, validDigest, (short) 1, "ATIVO");
    assertThat(inserted).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Constraint ck_ufu_enrollment_hmac_version_range rejeita versão de chave menor que 1")
  void checkConstraintRejectsHmacVersionLessThanOne() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-hmac-version");
    byte[] validDigest = createSampleDigest((byte) 0x11);

    // Versão 0
    assertConstraintViolation(
        identity, validDigest, (short) 0, "ATIVO", "ck_ufu_enrollment_hmac_version_range");
    // Versão negativa
    assertConstraintViolation(
        identity, validDigest, (short) -1, "ATIVO", "ck_ufu_enrollment_hmac_version_range");

    // Versão 1 e 2 são aceitas
    int inserted = insertEnrollmentDirect(identity, validDigest, (short) 1, "ATIVO");
    assertThat(inserted).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Constraint ck_ufu_enrollment_status_values rejeita status fora do vocabulário fechado ATIVO/LIBERADO")
  void checkConstraintRejectsInvalidStatus() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-status-values");
    byte[] validDigest = createSampleDigest((byte) 0x22);

    // Status inválidos
    assertConstraintViolation(
        identity, validDigest, (short) 1, "PENDENTE", "ck_ufu_enrollment_status_values");
    assertConstraintViolation(
        identity, validDigest, (short) 1, "EXPIRADO", "ck_ufu_enrollment_status_values");
    assertConstraintViolation(
        identity, validDigest, (short) 1, "INATIVO", "ck_ufu_enrollment_status_values");

    // Status válidos ATIVO e LIBERADO
    int inserted = insertEnrollmentDirect(identity, validDigest, (short) 1, "ATIVO");
    assertThat(inserted).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Constraint ck_ufu_enrollment_timestamp_order rejeita updated_at anterior a created_at")
  void checkConstraintRejectsTimestampOrderViolation() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-timestamp-order");
    byte[] validDigest = createSampleDigest((byte) 0x33);

    DataIntegrityViolationException timestampFailure =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.ufu_enrollment (
                            identity_id,
                            matricula_hmac,
                            hmac_version,
                            status,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 second')
                        """,
                    identity,
                    validDigest,
                    (short) 1,
                    "ATIVO"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(timestampFailure, "23514", "ck_ufu_enrollment_timestamp_order");
  }

  @Test
  @DisplayName("Chave estrangeira fk_ufu_enrollment_identity rejeita identidade inexistente")
  void foreignKeyEnforcesLocalIdentityExistence() {
    UUID nonExistentIdentity = UUID.randomUUID();
    byte[] validDigest = createSampleDigest((byte) 0x44);

    DataIntegrityViolationException fkFailure =
        catchThrowableOfType(
            () -> insertEnrollmentDirect(nonExistentIdentity, validDigest, (short) 1, "ATIVO"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(fkFailure, "23503", "fk_ufu_enrollment_identity");
  }

  @Test
  @DisplayName(
      "VERI-07: Índice único parcial uq_ufu_enrollment_active_hmac rejeita segundo vínculo ativo com o mesmo digest")
  void partialUniqueIndexRejectsDuplicateActiveMatriculaHmacAcrossDifferentIdentities() {
    UUID identity1 = createLocalIdentity("https://idp.invalid", "user-active-hmac-1");
    UUID identity2 = createLocalIdentity("https://idp.invalid", "user-active-hmac-2");
    byte[] sameDigest = createSampleDigest((byte) 0x55);

    // 1. Primeira conta vincula matrícula com sucesso
    int inserted = insertEnrollmentDirect(identity1, sameDigest, (short) 1, "ATIVO");
    assertThat(inserted).isEqualTo(1);

    // 2. Segunda conta tenta vincular a mesma matrícula -> violação do índice único parcial de HMAC
    DataIntegrityViolationException duplicateHmacFailure =
        catchThrowableOfType(
            () -> insertEnrollmentDirect(identity2, sameDigest, (short) 1, "ATIVO"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(duplicateHmacFailure, "23505", "uq_ufu_enrollment_active_hmac");

    Long totalActive =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
    assertThat(totalActive).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "D-31: Índice único parcial uq_ufu_enrollment_active_identity rejeita segundo vínculo ativo para a mesma conta")
  void partialUniqueIndexRejectsMultipleActiveEnrollmentsForSameIdentity() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-active-identity");
    byte[] digest1 = createSampleDigest((byte) 0x61);
    byte[] digest2 = createSampleDigest((byte) 0x62);

    // 1. Conta vincula primeira matrícula
    int inserted = insertEnrollmentDirect(identity, digest1, (short) 1, "ATIVO");
    assertThat(inserted).isEqualTo(1);

    // 2. Mesma conta tenta vincular outra matrícula sem liberar a anterior -> violação do índice
    // único parcial de identidade
    DataIntegrityViolationException duplicateIdentityFailure =
        catchThrowableOfType(
            () -> insertEnrollmentDirect(identity, digest2, (short) 1, "ATIVO"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(
        duplicateIdentityFailure, "23505", "uq_ufu_enrollment_active_identity");

    Long totalActive =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO'",
            Long.class,
            identity);
    assertThat(totalActive).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "D-32: Vínculos com status LIBERADO coexistem para mesmo HMAC e mesma identidade sem bloquear novo ATIVO")
  void partialUniqueIndexesAllowDuplicatesAcrossReleasedStatus() {
    UUID identity1 = createLocalIdentity("https://idp.invalid", "user-released-1");
    UUID identity2 = createLocalIdentity("https://idp.invalid", "user-released-2");
    UUID identity3 = createLocalIdentity("https://idp.invalid", "user-released-3");

    byte[] sharedDigest = createSampleDigest((byte) 0x77);
    byte[] newDigest = createSampleDigest((byte) 0x78);

    // 1. Vínculo liberado para identidade 1 com sharedDigest
    int ins1 = insertEnrollmentDirect(identity1, sharedDigest, (short) 1, "LIBERADO");
    assertThat(ins1).isEqualTo(1);

    // 2. Vínculo liberado para identidade 2 com sharedDigest (coexiste)
    int ins2 = insertEnrollmentDirect(identity2, sharedDigest, (short) 1, "LIBERADO");
    assertThat(ins2).isEqualTo(1);

    // 3. Novo vínculo ATIVO para identidade 3 com sharedDigest (permitido porque anteriores são
    // LIBERADOS)
    int ins3 = insertEnrollmentDirect(identity3, sharedDigest, (short) 1, "ATIVO");
    assertThat(ins3).isEqualTo(1);

    // 4. Novo vínculo ATIVO para identidade 1 com newDigest (permitido porque anterior desta
    // identidade é LIBERADO)
    int ins4 = insertEnrollmentDirect(identity1, newDigest, (short) 1, "ATIVO");
    assertThat(ins4).isEqualTo(1);

    Long totalRows =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.ufu_enrollment", Long.class);
    assertThat(totalRows).isEqualTo(4L);

    Long totalActive =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
    assertThat(totalActive).isEqualTo(2L);
  }

  @Test
  @Transactional
  @DisplayName("Repositório insere e relê digest binário e versão de chave sem conversão textual")
  void repositoryInsertsAndReadsActiveEnrollmentWithoutTextualConversion() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-repo-insert");
    byte[] expectedDigest = createSampleDigest((byte) 0x88);
    short expectedVersion = 2;

    int inserted = enrollmentRepository.insert(identity, expectedDigest, expectedVersion, "ATIVO");
    assertThat(inserted).isEqualTo(1);

    Optional<UfuEnrollment> activeOpt = enrollmentRepository.findActiveByIdentityId(identity);
    assertThat(activeOpt).isPresent();

    UfuEnrollment enrollment = activeOpt.get();
    assertThat(enrollment.getEnrollmentId()).isNotNull();
    assertThat(enrollment.getIdentityId()).isEqualTo(identity);
    assertThat(enrollment.getMatriculaHmac()).isEqualTo(expectedDigest);
    assertThat(enrollment.getHmacVersion()).isEqualTo(expectedVersion);
    assertThat(enrollment.getStatus()).isEqualTo("ATIVO");
    assertThat(enrollment.isActive()).isTrue();
    assertThat(enrollment.getCreatedAt()).isNotNull();
    assertThat(enrollment.getUpdatedAt()).isNotNull();
  }

  @Test
  @Transactional
  @DisplayName(
      "D-35: Repositório consulta existência ativa por pertinência a conjunto de digests multi-versão")
  void repositoryQueriesActiveEnrollmentByCandidateDigestsMultiVersion() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-repo-multiversion");
    byte[] digestV1 = createSampleDigest((byte) 0x91);
    byte[] digestV2 = createSampleDigest((byte) 0x92);
    byte[] digestV3 = createSampleDigest((byte) 0x93);

    // Insere vínculo ativo sob versão V1
    enrollmentRepository.insert(identity, digestV1, (short) 1, "ATIVO");

    // 1. Consulta conjunto contendo [V1, V2, V3] -> encontra ATIVO
    boolean existsWithMatch =
        enrollmentRepository.existsActiveByMatriculaHmacIn(List.of(digestV1, digestV2, digestV3));
    assertThat(existsWithMatch).isTrue();

    List<UfuEnrollment> activeList =
        enrollmentRepository.findActiveByMatriculaHmacIn(List.of(digestV1, digestV2, digestV3));
    assertThat(activeList).hasSize(1);
    assertThat(activeList.get(0).getMatriculaHmac()).isEqualTo(digestV1);

    // 2. Consulta conjunto contendo apenas [V2, V3] -> não encontra ATIVO
    boolean existsWithoutMatch =
        enrollmentRepository.existsActiveByMatriculaHmacIn(List.of(digestV2, digestV3));
    assertThat(existsWithoutMatch).isFalse();

    List<UfuEnrollment> emptyList =
        enrollmentRepository.findActiveByMatriculaHmacIn(List.of(digestV2, digestV3));
    assertThat(emptyList).isEmpty();

    // 3. Atualiza vínculo para LIBERADO -> consulta multi-versão com V1 não mais encontra ATIVO
    jdbcTemplate.update(
        "UPDATE public.ufu_enrollment SET status = 'LIBERADO' WHERE identity_id = ?", identity);

    boolean existsAfterRelease =
        enrollmentRepository.existsActiveByMatriculaHmacIn(List.of(digestV1, digestV2, digestV3));
    assertThat(existsAfterRelease).isFalse();
  }

  @Test
  @Transactional
  @DisplayName("Repositório findActiveByIdentityId devolve vazio quando vínculo está LIBERADO")
  void repositoryFindActiveByIdentityIdReturnsEmptyForReleasedEnrollment() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-repo-released");
    byte[] digest = createSampleDigest((byte) 0xAA);

    enrollmentRepository.insert(identity, digest, (short) 1, "LIBERADO");

    Optional<UfuEnrollment> activeOpt = enrollmentRepository.findActiveByIdentityId(identity);
    assertThat(activeOpt).isEmpty();

    List<UfuEnrollment> allEnrollments = enrollmentRepository.findAllByIdentityId(identity);
    assertThat(allEnrollments).hasSize(1);
    assertThat(allEnrollments.get(0).getStatus()).isEqualTo("LIBERADO");
    assertThat(allEnrollments.get(0).isActive()).isFalse();
  }

  private void assertConstraintViolation(
      UUID identity, byte[] digest, short version, String status, String expectedConstraint) {
    DataIntegrityViolationException failure =
        catchThrowableOfType(
            () -> insertEnrollmentDirect(identity, digest, version, status),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(failure, "23514", expectedConstraint);
  }

  private UUID createLocalIdentity(String issuer, String subject) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, ?, ?)",
        id,
        issuer,
        subject);
    return id;
  }

  private int insertEnrollmentDirect(
      UUID identityId, byte[] matriculaHmac, short hmacVersion, String status) {
    return jdbcTemplate.update(
        """
                INSERT INTO public.ufu_enrollment (
                    identity_id,
                    matricula_hmac,
                    hmac_version,
                    status
                ) VALUES (?, ?, ?, ?)
                """,
        identityId,
        matriculaHmac,
        hmacVersion,
        status);
  }

  private static byte[] createSampleDigest(byte seed) {
    byte[] digest = new byte[32];
    Arrays.fill(digest, seed);
    return digest;
  }

  private static void assertSqlStateAndConstraint(
      DataIntegrityViolationException failure, String expectedSqlState, String expectedConstraint) {
    assertThat(failure).isNotNull();
    assertThat(failure.getMostSpecificCause()).isInstanceOf(PSQLException.class);

    PSQLException postgresFailure = (PSQLException) failure.getMostSpecificCause();
    assertThat(postgresFailure.getSQLState()).isEqualTo(expectedSqlState);
    assertThat(postgresFailure.getServerErrorMessage()).isNotNull();
    assertThat(postgresFailure.getServerErrorMessage().getConstraint())
        .isEqualTo(expectedConstraint);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-enrollment-uniqueness-it")
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
              .clientName("BETUFU Enrollment Uniqueness IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
