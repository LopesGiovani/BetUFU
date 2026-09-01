package br.com.betufu.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-audit-immutability-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AuditImmutabilityIT.TestOidcConfiguration.class)
class AuditImmutabilityIT {

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  private static final String DATABASE_NAME = "betufu_audit_immutability";
  private static final String ADMIN_USER = "betufu_immutability_admin";
  private static final String ADMIN_PASSWORD = "betufu-immutability-admin-test-only";
  private static final String FLYWAY_USER = "betufu_immutability_flyway";
  private static final String FLYWAY_PASSWORD = "betufu-immutability-flyway-test-only";
  private static final String RUNTIME_USER = "betufu_immutability_runtime";
  private static final String RUNTIME_PASSWORD = "betufu-immutability-runtime-test-only";

  private static boolean rolesProvisioned;

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(ADMIN_USER)
          .withPassword(ADMIN_PASSWORD);

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    provisionDatabaseRoles();
    registry.add("BETUFU_DB_JDBC_URL", POSTGRESQL::getJdbcUrl);
    registry.add("BETUFU_DB_USER", () -> RUNTIME_USER);
    registry.add("BETUFU_DB_PASSWORD", () -> RUNTIME_PASSWORD);
    registry.add("BETUFU_FLYWAY_USER", () -> FLYWAY_USER);
    registry.add("BETUFU_FLYWAY_PASSWORD", () -> FLYWAY_PASSWORD);
  }

  @Autowired private JdbcTemplate runtimeJdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    JdbcTemplate migrator = migratorJdbcTemplate();
    migrator.execute("TRUNCATE TABLE public.audit_event CASCADE");
    migrator.execute("TRUNCATE TABLE public.local_identity CASCADE");
  }

  @Test
  @DisplayName(
      "Aplica migration V4 e valida colunas e constraints de public.audit_event no PostgreSQL 18")
  void appliesMigrationV4AndVerifiesTableContract() {
    JdbcTemplate migrator = migratorJdbcTemplate();
    List<String> columns =
        migrator.queryForList(
            """
                SELECT attribute.attname
                FROM pg_catalog.pg_attribute attribute
                JOIN pg_catalog.pg_class relation
                  ON relation.oid = attribute.attrelid
                JOIN pg_catalog.pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = 'audit_event'
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                ORDER BY attribute.attnum
                """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "event_id",
            "occurred_at",
            "actor_identity_id",
            "actor_role",
            "target_identity_id",
            "action",
            "outcome",
            "previous_value",
            "new_value",
            "reason",
            "correlation_id",
            "idempotency_key");

    List<String> constraints =
        migrator.queryForList(
            """
                SELECT constraint_record.conname
                FROM pg_catalog.pg_constraint constraint_record
                WHERE constraint_record.conrelid = 'public.audit_event'::regclass
                  AND constraint_record.contype IN ('p', 'u', 'c', 'f')
                ORDER BY constraint_record.conname
                """,
            String.class);

    assertThat(constraints)
        .contains(
            "pk_audit_event",
            "fk_audit_event_actor_identity",
            "fk_audit_event_target_identity",
            "ck_audit_event_actor_role_not_empty",
            "ck_audit_event_actor_role_octets",
            "ck_audit_event_action_not_empty",
            "ck_audit_event_action_octets",
            "ck_audit_event_action_values",
            "ck_audit_event_outcome_not_empty",
            "ck_audit_event_outcome_octets",
            "ck_audit_event_outcome_values",
            "ck_audit_event_previous_value_not_empty",
            "ck_audit_event_previous_value_octets",
            "ck_audit_event_new_value_not_empty",
            "ck_audit_event_new_value_octets",
            "ck_audit_event_reason_not_empty",
            "ck_audit_event_reason_octets",
            "ck_audit_event_correlation_id_not_empty",
            "ck_audit_event_correlation_id_octets",
            "ck_audit_event_idempotency_key_not_empty",
            "ck_audit_event_idempotency_key_octets");

    Integer triggerCount =
        migrator.queryForObject(
            """
                SELECT count(*)
                FROM pg_catalog.pg_trigger
                WHERE tgrelid = 'public.audit_event'::regclass
                  AND tgname = 'trg_prevent_audit_event_modification'
                """,
            Integer.class);
    assertThat(triggerCount).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "AUDT-02: Trigger BEFORE UPDATE recusa alteração com erro explícito e preserva linha intacta")
  void triggerRejectsUpdateWithObservableExceptionAndPreservesRow() {
    JdbcTemplate migrator = migratorJdbcTemplate();
    UUID eventId = UUID.randomUUID();
    migrator.update(
        """
                INSERT INTO public.audit_event (
                    event_id,
                    action,
                    outcome,
                    reason
                ) VALUES (?, ?, ?, ?)
                """,
        eventId,
        "PAPEL_CONCEDIDO",
        "PERMITIDO",
        "Motivo original legítimo");

    assertThatThrownBy(
            () ->
                migrator.update(
                    "UPDATE public.audit_event SET action = ? WHERE event_id = ?",
                    "PAPEL_ADULTERADO",
                    eventId))
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            ex -> {
              assertThat(ex.getMessage())
                  .contains("audit_event is immutable: UPDATE and DELETE are prohibited");
              if (ex instanceof UncategorizedSQLException uncategorized) {
                SQLException sqlEx = uncategorized.getSQLException();
                assertThat(sqlEx.getSQLState()).isEqualTo("P0001");
              }
            });

    String currentAction =
        migrator.queryForObject(
            "SELECT action FROM public.audit_event WHERE event_id = ?", String.class, eventId);
    assertThat(currentAction).isEqualTo("PAPEL_CONCEDIDO");
  }

  @Test
  @DisplayName(
      "AUDT-02: Trigger BEFORE DELETE recusa exclusão com erro explícito e preserva linha intacta")
  void triggerRejectsDeleteWithObservableExceptionAndPreservesRow() {
    JdbcTemplate migrator = migratorJdbcTemplate();
    UUID eventId = UUID.randomUUID();
    migrator.update(
        """
                INSERT INTO public.audit_event (
                    event_id,
                    action,
                    outcome,
                    reason
                ) VALUES (?, ?, ?, ?)
                """,
        eventId,
        "CORRECAO_MATRICULA",
        "PERMITIDO",
        "Motivo de auditoria");

    assertThatThrownBy(
            () -> migrator.update("DELETE FROM public.audit_event WHERE event_id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            ex -> {
              assertThat(ex.getMessage())
                  .contains("audit_event is immutable: UPDATE and DELETE are prohibited");
              if (ex instanceof UncategorizedSQLException uncategorized) {
                SQLException sqlEx = uncategorized.getSQLException();
                assertThat(sqlEx.getSQLState()).isEqualTo("P0001");
              }
            });

    Long count =
        migrator.queryForObject(
            "SELECT count(*) FROM public.audit_event WHERE event_id = ?", Long.class, eventId);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("AUDT-01: CHECK recusa action fora do vocabulario fechado")
  void databaseRejectsActionOutsideClosedVocabulary() {
    assertThatThrownBy(
            () ->
                runtimeJdbcTemplate.update(
                    "INSERT INTO public.audit_event (action, outcome) VALUES (?, ?)",
                    "ACAO_FORA_DO_CONTRATO",
                    "PERMITIDO"))
        .isInstanceOf(DataAccessException.class)
        .satisfies(exception -> assertSqlState(exception, "23514"));
  }

  @Test
  @DisplayName("AUDT-02: Runtime insere e le, mas nao altera, remove, trunca nem cria objeto")
  void runtimeHasOnlySelectAndInsertOnAuditTrailAndNoDdl() {
    UUID eventId = UUID.randomUUID();
    runtimeJdbcTemplate.update(
        "INSERT INTO public.audit_event (event_id, action, outcome) VALUES (?, ?, ?)",
        eventId,
        "LOGIN_EFETUADO",
        "PERMITIDO");

    assertThat(
            runtimeJdbcTemplate.queryForObject(
                "SELECT action FROM public.audit_event WHERE event_id = ?", String.class, eventId))
        .isEqualTo("LOGIN_EFETUADO");

    assertPrivilegeDenied(
        () ->
            runtimeJdbcTemplate.update(
                "UPDATE public.audit_event SET reason = 'negado' WHERE event_id = ?", eventId));
    assertPrivilegeDenied(
        () ->
            runtimeJdbcTemplate.update(
                "DELETE FROM public.audit_event WHERE event_id = ?", eventId));
    assertPrivilegeDenied(() -> runtimeJdbcTemplate.execute("TRUNCATE TABLE public.audit_event"));
    assertPrivilegeDenied(
        () -> runtimeJdbcTemplate.execute("CREATE TABLE public.runtime_ddl_canary (id integer)"));

    assertThat(
            migratorJdbcTemplate()
                .queryForObject(
                    "SELECT count(*) FROM public.audit_event WHERE event_id = ?",
                    Long.class,
                    eventId))
        .isEqualTo(1L);
  }

  private static void assertPrivilegeDenied(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOf(DataAccessException.class)
        .satisfies(exception -> assertSqlState(exception, "42501"));
  }

  private static void assertSqlState(Throwable exception, String expectedSqlState) {
    Throwable cause = exception;
    while (cause != null && !(cause instanceof SQLException)) {
      cause = cause.getCause();
    }
    assertThat(cause)
        .as("a excecao deve preservar o SQLException do PostgreSQL")
        .isInstanceOf(SQLException.class);
    assertThat(((SQLException) cause).getSQLState()).isEqualTo(expectedSqlState);
  }

  private static synchronized void provisionDatabaseRoles() {
    if (rolesProvisioned) {
      return;
    }
    if (!POSTGRESQL.isRunning()) {
      POSTGRESQL.start();
    }
    try (Connection connection =
            DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), ADMIN_USER, ADMIN_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE " + FLYWAY_USER + " LOGIN PASSWORD '" + FLYWAY_PASSWORD + "'");
      statement.execute(
          "CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
      statement.execute("ALTER DATABASE " + DATABASE_NAME + " OWNER TO " + FLYWAY_USER);
      statement.execute(
          "ALTER DATABASE " + DATABASE_NAME + " SET betufu.runtime_role TO '" + RUNTIME_USER + "'");
      statement.execute("REVOKE CONNECT, TEMPORARY ON DATABASE " + DATABASE_NAME + " FROM PUBLIC");
      statement.execute("GRANT CONNECT ON DATABASE " + DATABASE_NAME + " TO " + RUNTIME_USER);
      statement.execute("ALTER SCHEMA public OWNER TO " + FLYWAY_USER);
      statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
      statement.execute("REVOKE ALL PRIVILEGES ON SCHEMA public FROM " + RUNTIME_USER);
      statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + FLYWAY_USER
              + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
              + RUNTIME_USER);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES FOR ROLE "
              + FLYWAY_USER
              + " IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO "
              + RUNTIME_USER);
      rolesProvisioned = true;
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "could not provision isolated audit database roles", exception);
    }
  }

  private static JdbcTemplate migratorJdbcTemplate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUsername(FLYWAY_USER);
    dataSource.setPassword(FLYWAY_PASSWORD);
    return new JdbcTemplate(dataSource);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-audit-immutability-it")
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
              .clientName("BETUFU Audit Immutability IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
