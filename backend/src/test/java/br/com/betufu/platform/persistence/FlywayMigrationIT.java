package br.com.betufu.platform.persistence;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    @DisplayName("Aplica V1 e V2 sequencialmente no PostgreSQL 18 e verifica histórico autoritativo")
    void appliesV1AndV2MigrationsSequentially() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(2);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(2);

        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[0].getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
        assertThat(applied[1].getState()).isEqualTo(MigrationState.SUCCESS);

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
        assertThat(firstRun.migrationsExecuted).isEqualTo(2);

        MigrateResult secondRun = flyway.migrate();
        assertThat(secondRun.migrationsExecuted).isEqualTo(0);

        MigrateResult thirdRun = flyway.migrate();
        assertThat(thirdRun.migrationsExecuted).isEqualTo(0);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(2);
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
            assertThat(totalExecuted).isEqualTo(2);

            Flyway verificationFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();

            MigrationInfo[] applied = verificationFlyway.info().applied();
            assertThat(applied).hasSize(2);
            assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }
}
