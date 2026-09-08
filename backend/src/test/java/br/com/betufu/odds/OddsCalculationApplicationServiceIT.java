package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.market.OddsCalculationApplicationService;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OddsCalculationApplicationServiceIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("betufu_odds_calculation_test")
          .withUsername("betufu_odds_calculation_admin")
          .withPassword("betufu-odds-calculation-pass");

  private JdbcTemplate jdbc;
  private OddsMarketRepository repository;
  private SportsEventForOdds event;
  private UUID marketVersionId;
  private UUID historicalVersionId;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRES.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRES.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    repository = new OddsMarketRepository(jdbc, new DataSourceTransactionManager(dataSource));

    UUID eventId = UUID.randomUUID();
    UUID competitionId = UUID.randomUUID();
    UUID editionId = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    UUID away = UUID.randomUUID();
    event =
        new SportsEventForOdds(
            eventId,
            competitionId,
            editionId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-10-10T18:00:00Z"),
            "UTC",
            SportsEventForOdds.EventFormat.HEAD_TO_HEAD,
            List.of(
                new SportsEventForOdds.Slot(1, "Home", home, "Home FC"),
                new SportsEventForOdds.Slot(2, "Away", away, "Away FC")));
    UUID marketId = UUID.randomUUID();
    marketVersionId = UUID.randomUUID();
    repository.saveMarket(new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    jdbc.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'OPEN')",
        marketVersionId,
        marketId);
    repository.advanceMarketHead(marketId, marketVersionId, 0);
    historicalVersionId = UUID.randomUUID();
  }

  @Test
  void followsPublicSportsPortToKernelAndPersistsExactLineage() {
    SportsResultForOddsPort port = portWithHistory();
    OddsCalculationApplicationService service =
        new OddsCalculationApplicationService(port, repository);

    OddsCalculationRecord record =
        service.calculate(
            marketVersionId, event.eventId(), false, Instant.parse("2026-10-10T17:00:00Z"));

    assertThat(record.calculationId()).isNotNull();
    assertThat(record.marketVersionId()).isEqualTo(marketVersionId);
    assertThat(record.confirmedVersionId()).isEqualTo(historicalVersionId);
    assertThat(record.algorithmVersion()).isEqualTo("elo-davidson-v1");
    assertThat(record.configurationDigest()).startsWith("sha256:");
    assertThat(record.fairProbability()).isBetween(new BigDecimal("0"), new BigDecimal("1"));
    assertThat(record.inputSnapshot())
        .contains(event.eventId().toString(), historicalVersionId.toString());
    assertThat(repository.findCalculation(record.calculationId())).contains(record);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.odds_calculation WHERE calculation_id = ?",
                Long.class,
                record.calculationId()))
        .isEqualTo(1L);
  }

  @Test
  void eachRecalculationIsAnImmutableFactAndDoesNotReadBettingVolume() {
    SportsResultForOddsPort port = portWithHistory();
    OddsCalculationApplicationService service =
        new OddsCalculationApplicationService(port, repository);
    OddsCalculationRecord first =
        service.calculate(
            marketVersionId, event.eventId(), false, Instant.parse("2026-10-10T17:00:00Z"));
    OddsCalculationRecord second =
        service.calculate(
            marketVersionId, event.eventId(), false, Instant.parse("2026-10-10T17:00:00Z"));
    assertThat(second.fairProbability()).isEqualByComparingTo(first.fairProbability());
    assertThat(second.configurationDigest()).isEqualTo(first.configurationDigest());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.odds_calculation WHERE market_version_id = ?",
                Long.class,
                marketVersionId))
        .isEqualTo(2L);
  }

  private SportsResultForOddsPort portWithHistory() {
    ConfirmedSportsResultForOdds history =
        new ConfirmedSportsResultForOdds(
            historicalVersionId,
            UUID.randomUUID(),
            event.competitionId(),
            event.editionId(),
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(
                new Score(1, 2, event.slots().get(0).teamId(), "Home"),
                new Score(2, 0, event.slots().get(1).teamId(), "Away")),
            Instant.parse("2026-10-01T18:00:00Z"),
            Instant.parse("2026-10-01T19:00:00Z"),
            Instant.parse("2026-10-01T18:00:00Z"),
            null);
    return new SportsResultForOddsPort() {
      @Override
      public Optional<SportsEventForOdds> getEventForOdds(UUID eventId) {
        return event.eventId().equals(eventId) ? Optional.of(event) : Optional.empty();
      }

      @Override
      public Optional<ConfirmedSportsResultForOdds> getConfirmedResultForOdds(
          UUID eventId, UUID confirmedVersionId) {
        return Optional.empty();
      }

      @Override
      public List<ConfirmedSportsResultForOdds> findAdmissibleResultsAsOf(
          UUID competitionId, UUID editionId, Instant asOfCutoff) {
        return List.of(history);
      }
    };
  }
}
