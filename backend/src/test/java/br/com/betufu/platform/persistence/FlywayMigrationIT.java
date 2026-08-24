package br.com.betufu.platform.persistence;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayMigrationIT {

    private static final String RUNTIME_USER = "betufu_migration_runtime";
    private static final String RUNTIME_PASSWORD = "betufu-migration-runtime-test-only";

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_migration_test")
            .withUsername("betufu_migration_user")
            .withPassword("betufu-migration-pass");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRESQL.getJdbcUrl());
        dataSource.setUsername(POSTGRESQL.getUsername());
        dataSource.setPassword(POSTGRESQL.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'betufu_migration_runtime') THEN
                        CREATE ROLE betufu_migration_runtime LOGIN;
                    END IF;
                END $$
                """);
        jdbcTemplate.execute("ALTER ROLE " + RUNTIME_USER + " PASSWORD '" + RUNTIME_PASSWORD + "'");
        jdbcTemplate.execute("ALTER DATABASE " + POSTGRESQL.getDatabaseName()
                + " SET betufu.runtime_role TO '" + RUNTIME_USER + "'");
        jdbcTemplate.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        jdbcTemplate.execute("REVOKE ALL PRIVILEGES ON SCHEMA public FROM " + RUNTIME_USER);
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
        jdbcTemplate.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public"
                + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + RUNTIME_USER);
        jdbcTemplate.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public"
                + " GRANT USAGE, SELECT ON SEQUENCES TO " + RUNTIME_USER);
    }

    @Test
    @DisplayName("Aplica V1 a V6 sequencialmente no PostgreSQL 18 e verifica histórico autoritativo")
    void appliesV1AndV2MigrationsSequentially() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(9);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(9);

        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[0].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
        assertThat(applied[1].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[2].getVersion().getVersion()).isEqualTo("3");
        assertThat(applied[2].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[3].getVersion().getVersion()).isEqualTo("4");
        assertThat(applied[3].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[4].getVersion().getVersion()).isEqualTo("5");
        assertThat(applied[4].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[5].getVersion().getVersion()).isEqualTo("5.1");
        assertThat(applied[5].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[6].getVersion().getVersion()).isEqualTo("6");
        assertThat(applied[6].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[7].getVersion().getVersion()).isEqualTo("7");
        assertThat(applied[7].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[8].getVersion().getVersion()).isEqualTo("8");
        assertThat(applied[8].getState()).isEqualTo(MigrationState.SUCCESS);

        Integer guardCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.platform_guard WHERE guard_key = 'PLATFORM_SCHEMA_AUTHORITATIVE'",
                Integer.class);
        assertThat(guardCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Execuções repetidas do migrate() são estritamente idempotentes")
    void reapplyingMigrationsIsIdempotent() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        MigrateResult firstRun = flyway.migrate();
        assertThat(firstRun.migrationsExecuted).isEqualTo(9);

        MigrateResult secondRun = flyway.migrate();
        assertThat(secondRun.migrationsExecuted).isEqualTo(0);

        MigrateResult thirdRun = flyway.migrate();
        assertThat(thirdRun.migrationsExecuted).isEqualTo(0);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(9);
        assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
    }

    @Test
    @DisplayName("Inicializações concorrentes do Flyway serializam via advisory lock sem corrupção")
    void concurrentFlywayInitializationsSerializeCleanly() throws Exception {
        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MigrateResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> {
                    Flyway flyway = Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration")
                            .load();

                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    return flyway.migrate();
                }));
            }

            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();

            List<MigrateResult> results = new ArrayList<>();
            for (Future<MigrateResult> future : futures) {
                results.add(future.get(30, SECONDS));
            }

            assertThat(results).allSatisfy(r -> assertThat(r.success).isTrue());

            int totalExecuted = results.stream().mapToInt(r -> r.migrationsExecuted).sum();
            assertThat(totalExecuted).isEqualTo(9);

            Flyway verificationFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();

            MigrationInfo[] applied = verificationFlyway.info().applied();
            assertThat(applied).hasSize(9);
            assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("V7 impede papel escopado sem escopo e papel global com escopo no proprio banco")
    void v7EnforcesRoleScopeConsistencyAtDatabaseLevel() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        java.util.UUID identityId = jdbcTemplate.queryForObject(
                "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
                java.util.UUID.class,
                "https://example.invalid/realms/betufu-check",
                "subject-scope-check");

        // Papel escopado sem escopo: recusado pelo banco, nao apenas pelo servico
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'MODERADOR', NULL, 'VIGENTE')",
                identityId))
                .as("MODERADOR sem scope_ref deve violar ck_operational_role_grant_scope")
                .hasMessageContaining("ck_operational_role_grant_scope");

        // Papel global com escopo: igualmente recusado
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'ADMINISTRADOR_GERAL', gen_random_uuid(), 'VIGENTE')",
                identityId))
                .as("ADMINISTRADOR_GERAL com scope_ref deve violar ck_operational_role_grant_scope")
                .hasMessageContaining("ck_operational_role_grant_scope");

        // Controle positivo: as duas combinacoes validas passam, senao a CHECK estaria proibindo tudo
        assertThat(jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'MODERADOR', gen_random_uuid(), 'VIGENTE')",
                identityId)).isEqualTo(1);
        assertThat(jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'ADMINISTRADOR_GERAL', NULL, 'VIGENTE')",
                identityId)).isEqualTo(1);
    }
}
