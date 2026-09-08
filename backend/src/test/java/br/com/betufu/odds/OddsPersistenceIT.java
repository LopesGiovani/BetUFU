package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OddsPersistenceIT {

  private static final String IMAGE =
      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_odds_persistence_test")
          .withUsername("betufu_odds_persistence_admin")
          .withPassword("betufu-odds-persistence-pass");

  private JdbcTemplate jdbcTemplate;
  private OddsMarketRepository repository;

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
    Flyway.configure().dataSource(dataSource).load().migrate();
    repository =
        new OddsMarketRepository(jdbcTemplate, new DataSourceTransactionManager(dataSource));
  }

  @Test
  @DisplayName("repositório grava e relê mercado e cálculo com lineage exata")
  void savesAndReadsMarketAndCalculation() {
    UUID marketId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    OddsMarketRecord market =
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, Instant.now(), Instant.now());
    repository.saveMarket(market);
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'OPEN')",
        versionId,
        marketId);
    assertThat(repository.advanceMarketHead(marketId, versionId, 0)).isTrue();
    assertThat(repository.findMarket(marketId)).isPresent();

    OddsCalculationRecord calculation =
        new OddsCalculationRecord(
            calculationId,
            versionId,
            "elo-v1",
            "sha256:config",
            "{\"event\":\"fixture\"}",
            UUID.randomUUID(),
            new BigDecimal("0.5000000000"),
            new BigDecimal("0.8000000000"),
            Instant.now(),
            Instant.now());
    OddsCalculationRecord persisted = repository.saveCalculation(calculation);

    assertThat(persisted.calculationId()).isEqualTo(calculationId);
    assertThat(repository.findCalculation(calculationId)).contains(persisted);
    assertThat(repository.findCalculation(calculationId).orElseThrow().inputSnapshot())
        .contains("fixture");
  }

  @Test
  @DisplayName("chave idempotente repete o mesmo resultado e rejeita payload divergente")
  void idempotencyIsRetrySafe() {
    UUID marketId = UUID.randomUUID();
    repository.saveMarket(
        new OddsMarketRecord(marketId, UUID.randomUUID(), "H2H", null, 0, null, null),
        UUID.randomUUID(),
        "market-command-1",
        "digest-a");
    long before =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.odds_market", Long.class);

    repository.saveMarket(
        new OddsMarketRecord(marketId, UUID.randomUUID(), "H2H", null, 0, null, null),
        UUID.randomUUID(),
        "market-command-2",
        "digest-a");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.odds_market", Long.class))
        .isEqualTo(before);

    UUID actor = UUID.randomUUID();
    OddsMarketRecord record =
        new OddsMarketRecord(UUID.randomUUID(), UUID.randomUUID(), "H2H", null, 0, null, null);
    repository.saveMarket(record, actor, "same-key", "digest-a");
    assertThatThrownBy(() -> repository.saveMarket(record, actor, "same-key", "digest-b"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different payload");

    UUID calculationMarketId = UUID.randomUUID();
    UUID calculationVersionId = UUID.randomUUID();
    repository.saveMarket(
        new OddsMarketRecord(calculationMarketId, UUID.randomUUID(), "H2H", null, 0, null, null));
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'OPEN')",
        calculationVersionId,
        calculationMarketId);
    OddsCalculationRecord calculation =
        new OddsCalculationRecord(
            UUID.randomUUID(),
            calculationVersionId,
            "elo-v1",
            "sha256:retry-safe",
            "{\"event\":\"retry-safe\"}",
            UUID.randomUUID(),
            new BigDecimal("0.5000000000"),
            new BigDecimal("0.8000000000"),
            Instant.now(),
            Instant.now());
    UUID calculationActor = UUID.randomUUID();

    OddsCalculationRecord first =
        repository.saveCalculation(
            calculation, calculationActor, "calculation-same-key", "calculation-digest");
    OddsCalculationRecord retried =
        repository.saveCalculation(
            calculation, calculationActor, "calculation-same-key", "calculation-digest");

    assertThat(retried).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.odds_calculation WHERE calculation_id = ?",
                Long.class,
                calculation.calculationId()))
        .isEqualTo(1L);
  }
}
