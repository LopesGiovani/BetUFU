package br.com.betufu.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-audit-event-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused"
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AuditEventIT.TestOidcConfiguration.class)
class AuditEventIT {

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    private static final String DATABASE_NAME = "betufu_audit_event";
    private static final String ADMIN_USER = "betufu_audit_admin";
    private static final String ADMIN_PASSWORD = "betufu-audit-admin-test-only";
    private static final String FLYWAY_USER = "betufu_audit_flyway";
    private static final String FLYWAY_PASSWORD = "betufu-audit-flyway-test-only";
    private static final String RUNTIME_USER = "betufu_audit_runtime";
    private static final String RUNTIME_PASSWORD = "betufu-audit-runtime-test-only";

    private static boolean rolesProvisioned;

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
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

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        JdbcTemplate migrator = migratorJdbcTemplate();
        migrator.execute("TRUNCATE TABLE public.audit_event CASCADE");
        migrator.execute("TRUNCATE TABLE public.betufu_profile CASCADE");
        migrator.execute("TRUNCATE TABLE public.local_identity CASCADE");
        MDC.clear();
    }

    private static synchronized void provisionDatabaseRoles() {
        if (rolesProvisioned) {
            return;
        }
        if (!POSTGRESQL.isRunning()) {
            POSTGRESQL.start();
        }
        try (Connection connection = DriverManager.getConnection(
                        POSTGRESQL.getJdbcUrl(), ADMIN_USER, ADMIN_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + FLYWAY_USER + " LOGIN PASSWORD '" + FLYWAY_PASSWORD + "'");
            statement.execute("CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
            statement.execute("ALTER DATABASE " + DATABASE_NAME + " OWNER TO " + FLYWAY_USER);
            statement.execute("ALTER DATABASE " + DATABASE_NAME
                    + " SET betufu.runtime_role TO '" + RUNTIME_USER + "'");
            statement.execute("REVOKE CONNECT, TEMPORARY ON DATABASE " + DATABASE_NAME + " FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE " + DATABASE_NAME + " TO " + RUNTIME_USER);
            statement.execute("ALTER SCHEMA public OWNER TO " + FLYWAY_USER);
            statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            statement.execute("REVOKE ALL PRIVILEGES ON SCHEMA public FROM " + RUNTIME_USER);
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + FLYWAY_USER
                    + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + RUNTIME_USER);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + FLYWAY_USER
                    + " IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO " + RUNTIME_USER);
            rolesProvisioned = true;
        } catch (SQLException exception) {
            throw new IllegalStateException("could not provision isolated audit database roles", exception);
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

    @Test
    @DisplayName("AUDT-01: Grava evento permitido e relê conferindo todos os campos")
    void recordsPermittedEventAndVerifiesAllFields() {
        UUID actorId = createLocalIdentity("https://idp.invalid", "actor-user");
        UUID targetId = createLocalIdentity("https://idp.invalid", "target-user");
        String idempotencyKey = "idemp-permitted-" + UUID.randomUUID();
        String correlationId = "corr-permitted-" + UUID.randomUUID();

        AuditEntry entry = AuditEntry.builder()
                .actorIdentityId(actorId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetId)
                .action("PAPEL_CONCEDIDO")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .previousValue(null)
                .newValue("MODERADOR")
                .reason("Concessão operacional aprovada")
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .build();

        auditRecorder.record(entry);

        AuditEvent persisted = auditEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();

        assertThat(persisted.getEventId()).isNotNull();
        assertThat(persisted.getOccurredAt()).isNotNull();
        assertThat(persisted.getActorIdentityId()).isEqualTo(actorId);
        assertThat(persisted.getActorRole()).isEqualTo("ADMINISTRADOR_GERAL");
        assertThat(persisted.getTargetIdentityId()).isEqualTo(targetId);
        assertThat(persisted.getAction()).isEqualTo("PAPEL_CONCEDIDO");
        assertThat(persisted.getOutcome()).isEqualTo("PERMITIDO");
        assertThat(persisted.getPreviousValue()).isNull();
        assertThat(persisted.getNewValue()).isEqualTo("MODERADOR");
        assertThat(persisted.getReason()).isEqualTo("Concessão operacional aprovada");
        assertThat(persisted.getCorrelationId()).isEqualTo(correlationId);
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("AUDT-01: Grava evento bloqueado e relê conferindo todos os campos")
    void recordsBlockedEventAndVerifiesAllFields() {
        UUID actorId = createLocalIdentity("https://idp.invalid", "blocked-actor");
        String idempotencyKey = "idemp-blocked-" + UUID.randomUUID();
        String correlationId = "corr-blocked-" + UUID.randomUUID();

        AuditEntry entry = AuditEntry.builder()
                .actorIdentityId(actorId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(actorId)
                .action("AUTOCONCESSAO_PAPEL")
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .previousValue(null)
                .newValue("ADMINISTRADOR_GERAL")
                .reason("Tentativa de autoconcessão bloqueada pela guarda D-48a")
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .build();

        auditRecorder.record(entry);

        AuditEvent persisted = auditEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();

        assertThat(persisted.getEventId()).isNotNull();
        assertThat(persisted.getOccurredAt()).isNotNull();
        assertThat(persisted.getActorIdentityId()).isEqualTo(actorId);
        assertThat(persisted.getActorRole()).isEqualTo("ADMINISTRADOR_GERAL");
        assertThat(persisted.getTargetIdentityId()).isEqualTo(actorId);
        assertThat(persisted.getAction()).isEqualTo("AUTOCONCESSAO_PAPEL");
        assertThat(persisted.getOutcome()).isEqualTo("BLOQUEADO");
        assertThat(persisted.getPreviousValue()).isNull();
        assertThat(persisted.getNewValue()).isEqualTo("ADMINISTRADOR_GERAL");
        assertThat(persisted.getReason()).isEqualTo("Tentativa de autoconcessão bloqueada pela guarda D-48a");
        assertThat(persisted.getCorrelationId()).isEqualTo(correlationId);
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("AUDT-01: Idempotência sob concorrência/repetição garante exatamente uma linha para a mesma chave")
    void repeatedRecordWithSameIdempotencyKeyPersistsExactlyOnce() {
        UUID targetId = createLocalIdentity("https://idp.invalid", "idempotent-target");
        String idempotencyKey = "idemp-dup-" + UUID.randomUUID();

        AuditEntry first = AuditEntry.builder()
                .targetIdentityId(targetId)
                .action("REGISTRO_MATRICULA")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .newValue("vinc_ref_01")
                .idempotencyKey(idempotencyKey)
                .build();

        AuditEntry second = AuditEntry.builder()
                .targetIdentityId(targetId)
                .action("REGISTRO_MATRICULA")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .newValue("vinc_ref_01")
                .idempotencyKey(idempotencyKey)
                .build();

        auditRecorder.record(first);
        auditRecorder.record(second);

        List<AuditEvent> all = auditEventRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("AUDT-01: Eventos sem chave de idempotência não colapsam e geram registros independentes")
    void recordsWithoutIdempotencyKeyProduceDistinctEntries() {
        UUID targetId = createLocalIdentity("https://idp.invalid", "distinct-target");

        AuditEntry first = AuditEntry.builder()
                .targetIdentityId(targetId)
                .action("LOGIN_EFETUADO")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .build();

        AuditEntry second = AuditEntry.builder()
                .targetIdentityId(targetId)
                .action("LOGIN_EFETUADO")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .build();

        auditRecorder.record(first);
        auditRecorder.record(second);

        List<AuditEvent> all = auditEventRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getIdempotencyKey()).isNull();
        assertThat(all.get(1).getIdempotencyKey()).isNull();
        assertThat(all.get(0).getEventId()).isNotEqualTo(all.get(1).getEventId());
    }

    @Test
    @DisplayName("AUDT-01: Extrai correlationId automaticamente do MDC quando omitido em AuditEntry")
    void extractsCorrelationIdFromMdcWhenAbsentInEntry() {
        String mdcCorrelationId = "mdc-corr-" + UUID.randomUUID();
        MDC.put("correlationId", mdcCorrelationId);

        AuditEntry entry = AuditEntry.builder()
                .action("CONSULTA_OPERACIONAL")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .idempotencyKey("idemp-mdc-" + UUID.randomUUID())
                .build();

        auditRecorder.record(entry);

        AuditEvent persisted = auditEventRepository.findByIdempotencyKey(entry.idempotencyKey()).orElseThrow();
        assertThat(persisted.getCorrelationId()).isEqualTo(mdcCorrelationId);
    }

    @Test
    @DisplayName("D-47: Suporta eventos de sistema com actor_identity_id e actor_role nulos")
    void supportsSystemActionsWithNullActor() {
        UUID targetId = createLocalIdentity("https://idp.invalid", "first-admin");
        String idempotencyKey = "idemp-system-" + UUID.randomUUID();

        AuditEntry entry = AuditEntry.builder()
                .actorIdentityId(null)
                .actorRole(null)
                .targetIdentityId(targetId)
                .action("SEMENTE_ADMINISTRADOR_INICIAL")
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .newValue("ADMINISTRADOR_GERAL")
                .reason("Bootstrap de primeiro administrador via variável BETUFU_SEED_ADMIN")
                .idempotencyKey(idempotencyKey)
                .build();

        auditRecorder.record(entry);

        AuditEvent persisted = auditEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(persisted.getActorIdentityId()).isNull();
        assertThat(persisted.getActorRole()).isNull();
        assertThat(persisted.getTargetIdentityId()).isEqualTo(targetId);
        assertThat(persisted.getAction()).isEqualTo("SEMENTE_ADMINISTRADOR_INICIAL");
    }

    @Test
    @DisplayName("AUDT-01: replay de reset usa acao especifica do vocabulario fechado")
    void recordsPasswordResetReplayActionFromClosedVocabulary() {
        UUID actorId = createLocalIdentity("https://idp.invalid", "password-reset-replay-admin");
        String idempotencyKey = "password-reset-replay-test-" + UUID.randomUUID();

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(actorId)
                .actorRole("ADMINISTRADOR_GERAL")
                .action(AuditEntry.ACTION_REPROCESSAMENTO_RESET_SENHA)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .previousValue("FAILED")
                .newValue("CLAIMED")
                .reason("Password reset replay INTENT: sourceEventId=opaque-test-event")
                .idempotencyKey(idempotencyKey)
                .build());

        AuditEvent persisted = auditEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(persisted.getAction()).isEqualTo("REPROCESSAMENTO_RESET_SENHA");
        assertThat(persisted.getPreviousValue()).isEqualTo("FAILED");
        assertThat(persisted.getNewValue()).isEqualTo("CLAIMED");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-audit-event-it")
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
                    .clientName("BETUFU Audit Event IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
