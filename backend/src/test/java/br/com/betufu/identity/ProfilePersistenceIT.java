package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-profile-persistence-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(ProfilePersistenceIT.TestOidcConfiguration.class)
class ProfilePersistenceIT {

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

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
  }

  @Test
  @DisplayName(
      "Aplica migration V3 e valida colunas e constraints de public.betufu_profile no PostgreSQL 18")
  void appliesMigrationV3AndVerifiesTableContract() {
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
                  AND relation.relname = 'betufu_profile'
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                ORDER BY attribute.attnum
                """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "profile_id",
            "identity_id",
            "nickname",
            "favorite_team_id",
            "nickname_updated_at",
            "created_at",
            "updated_at",
            "favorite_organization_id");

    List<String> constraints =
        jdbcTemplate.queryForList(
            """
                SELECT constraint_record.conname
                FROM pg_catalog.pg_constraint constraint_record
                WHERE constraint_record.conrelid = 'public.betufu_profile'::regclass
                  AND constraint_record.contype IN ('p', 'u', 'c', 'f')
                ORDER BY constraint_record.conname
                """,
            String.class);

    assertThat(constraints)
        .contains(
            "pk_betufu_profile",
            "uq_betufu_profile_identity_id",
            "fk_betufu_profile_identity",
            "ck_betufu_profile_nickname_not_empty",
            "ck_betufu_profile_nickname_octets",
            "ck_betufu_profile_nickname_pattern",
            "ck_betufu_profile_timestamp_order");
  }

  @Test
  @DisplayName(
      "D-44: Constraint de padrão POSIX aceita ASCII estrito válido e rejeita homoglifo cirílico")
  void posixRegexCheckConstraintRejectsCyrillicHomoglyphAndAcceptsValidAscii() {
    UUID identityValid = createLocalIdentity("https://idp.invalid", "user-valid");
    UUID identityHomoglyph = createLocalIdentity("https://idp.invalid", "user-homoglyph");

    // 1. Inserção válida com ASCII estrito (letras, números, hífen, sublinhado)
    int inserted = insertProfile(identityValid, "giovani_123");
    assertThat(inserted).isEqualTo(1);

    // 2. Inserção com homoglifo cirílico da letra 'a' (\u0430)
    String homoglyphNickname = "giov\u0430ni";
    DataIntegrityViolationException homoglyphFailure =
        catchThrowableOfType(
            () -> insertProfile(identityHomoglyph, homoglyphNickname),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(homoglyphFailure, "23514", "ck_betufu_profile_nickname_pattern");

    Long totalProfiles =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.betufu_profile", Long.class);
    assertThat(totalProfiles).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "D-44: Constraint de padrão POSIX rejeita espaços, acentos, símbolos e tamanhos fora de 3..20")
  void posixRegexCheckConstraintRejectsInvalidFormats() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-formats");

    // Espaço
    assertPatternViolation(identity, "user name");
    // Acentos
    assertPatternViolation(identity, "usuário");
    // Símbolos
    assertPatternViolation(identity, "user@123");
    assertPatternViolation(identity, "user.name");
    assertPatternViolation(identity, "user$");
    assertPatternViolation(identity, "user#test");
    // Menor que 3 caracteres
    assertPatternViolation(identity, "ab");
    assertPatternViolation(identity, "a");
    // Maior que 20 caracteres
    assertOctetViolation(identity, "123456789012345678901");
  }

  @Test
  @DisplayName("D-39: Constraint de unicidade garante um único perfil por identity_id")
  void enforcesUniqueConstraintOnIdentityId() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-unique");
    insertProfile(identity, "first_nick");

    DataIntegrityViolationException duplicateFailure =
        catchThrowableOfType(
            () -> insertProfile(identity, "second_nick"), DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(duplicateFailure, "23505", "uq_betufu_profile_identity_id");
  }

  @Test
  @DisplayName("Chave estrangeira fk_betufu_profile_identity exige linha em local_identity")
  void enforcesForeignKeyToLocalIdentity() {
    UUID nonExistentIdentity = UUID.randomUUID();

    DataIntegrityViolationException fkFailure =
        catchThrowableOfType(
            () -> insertProfile(nonExistentIdentity, "valid_nick"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(fkFailure, "23503", "fk_betufu_profile_identity");
  }

  @Test
  @DisplayName(
      "Constraint ck_betufu_profile_timestamp_order rejeita updated_at anterior a created_at")
  void enforcesTimestampOrderCheck() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-timestamp");

    DataIntegrityViolationException timestampFailure =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.betufu_profile (
                            identity_id,
                            nickname,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 second')
                        """,
                    identity,
                    "valid_nick"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(timestampFailure, "23514", "ck_betufu_profile_timestamp_order");
  }

  @Test
  @DisplayName(
      "D-45: favorite_team_id é anulável e aceita UUID arbitrário sem foreign key na Fase 2")
  void favoriteTeamIdIsNullableAndAcceptsUuidWithoutFk() {
    UUID identity1 = createLocalIdentity("https://idp.invalid", "user-team-null");
    UUID identity2 = createLocalIdentity("https://idp.invalid", "user-team-uuid");

    // Nulo por padrão
    insertProfile(identity1, "nick_team_null");

    // UUID arbitrário sem tabela de times existente
    UUID arbitraryTeamId = UUID.randomUUID();
    int inserted =
        jdbcTemplate.update(
            """
                INSERT INTO public.betufu_profile (identity_id, nickname, favorite_team_id)
                VALUES (?, ?, ?)
                """,
            identity2,
            "nick_team_uuid",
            arbitraryTeamId);

    assertThat(inserted).isEqualTo(1);

    UUID savedTeamId =
        jdbcTemplate.queryForObject(
            "SELECT favorite_team_id FROM public.betufu_profile WHERE identity_id = ?",
            UUID.class,
            identity2);
    assertThat(savedTeamId).isEqualTo(arbitraryTeamId);
  }

  @Test
  @DisplayName("V11 preserva o legado e aceita favorito de organização somente com FK válida")
  void favoriteOrganizationRequiresExistingOrganizationAndLegacyRemainsUntouched() {
    UUID identity = createLocalIdentity("https://idp.invalid", "user-organization-favorite");
    insertProfile(identity, "nick_org");
    UUID arbitraryLegacy = UUID.randomUUID();
    jdbcTemplate.update(
        "UPDATE public.betufu_profile SET favorite_team_id = ? WHERE identity_id = ?",
        arbitraryLegacy,
        identity);

    UUID organizationId = UUID.randomUUID();
    DataIntegrityViolationException missingOrganization =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.betufu_profile SET favorite_organization_id = ? WHERE identity_id = ?",
                    organizationId,
                    identity),
            DataIntegrityViolationException.class);
    assertSqlStateAndConstraint(
        missingOrganization, "23503", "fk_betufu_profile_favorite_organization");

    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) "
            + "VALUES (?, ?, ?, 'PUBLICADO')",
        organizationId,
        "Organização Favorita",
        "organizacao-favorita");
    jdbcTemplate.update(
        "UPDATE public.betufu_profile SET favorite_organization_id = ? WHERE identity_id = ?",
        organizationId,
        identity);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT favorite_organization_id FROM public.betufu_profile WHERE identity_id = ?",
                UUID.class,
                identity))
        .isEqualTo(organizationId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT favorite_team_id FROM public.betufu_profile WHERE identity_id = ?",
                UUID.class,
                identity))
        .isEqualTo(arbitraryLegacy);
  }

  private void assertPatternViolation(UUID identity, String invalidNickname) {
    assertConstraintViolation(identity, invalidNickname, "ck_betufu_profile_nickname_pattern");
  }

  private void assertOctetViolation(UUID identity, String invalidNickname) {
    assertConstraintViolation(identity, invalidNickname, "ck_betufu_profile_nickname_octets");
  }

  private void assertConstraintViolation(
      UUID identity, String invalidNickname, String expectedConstraint) {
    DataIntegrityViolationException failure =
        catchThrowableOfType(
            () -> insertProfile(identity, invalidNickname), DataIntegrityViolationException.class);

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

  private int insertProfile(UUID identityId, String nickname) {
    return jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
        identityId,
        nickname);
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
              .clientId("betufu-profile-persistence-it")
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
              .clientName("BETUFU Profile Persistence IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
