package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OddsMigrationIT {

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_odds_migration_test")
          .withUsername("betufu_odds_migration_admin")
          .withPassword("betufu-odds-migration-pass");

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUsername(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbcTemplate.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRESQL.getUsername()
            + "'");
  }

  @Test
  @DisplayName("V25 cria a linhagem odds depois de V24 sem objetos financeiros")
  void appliesOddsMigrationAfterV24() {
    Flyway flyway = Flyway.configure().dataSource(jdbcTemplate.getDataSource()).load();

    MigrateResult result = flyway.migrate();

    assertThat(result.success).isTrue();
    assertThat(result.migrationsExecuted).isEqualTo(27);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('odds_market', 'odds_market_version', 'odds_calculation', 'odds_publication', 'odds_lifecycle', 'odds_override', 'odds_audit_event', 'odds_idempotency_command')",
                Integer.class))
        .isEqualTo(8);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('bet', 'bets', 'ledger', 'settlement', 'liquidity', 'exposure')",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("V25 grava e le mercado/calculo e recusa reescrita historica")
  void persistsAndProtectsLineageFacts() {
    Flyway.configure().dataSource(jdbcTemplate.getDataSource()).load().migrate();
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, ?)",
        marketId,
        UUID.randomUUID(),
        "H2H");
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, ?, ?)",
        versionId,
        marketId,
        1,
        "OPEN");
    jdbcTemplate.update(
        "UPDATE public.odds_market SET current_version_id = ?, lock_version = 1 WHERE market_id = ?",
        versionId,
        marketId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_calculation (calculation_id, market_version_id, algorithm_version, configuration_digest, input_snapshot, fair_probability, confidence) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)",
        calculationId,
        versionId,
        "elo-v1",
        "sha256:test",
        "{\"source\":\"fixture\"}",
        0.5,
        0.8);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT fair_probability FROM public.odds_calculation WHERE calculation_id = ?",
                Double.class,
                calculationId))
        .isEqualTo(0.5);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.odds_calculation SET algorithm_version = 'tampered' WHERE calculation_id = ?",
                    calculationId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("odds history is immutable");
  }
}
