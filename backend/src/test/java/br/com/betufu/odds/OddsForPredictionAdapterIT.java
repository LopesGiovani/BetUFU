package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.OddsMarketModels.OddsMarketVersion;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.odds.internal.persistence.OddsPublicationRecord;
import br.com.betufu.odds.internal.prediction.OddsForPredictionAdapter;
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
class OddsForPredictionAdapterIT {

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_odds_prediction_snapshot_test")
          .withUsername("betufu_odds_prediction_snapshot_admin")
          .withPassword("betufu-odds-prediction-snapshot-pass");

  private JdbcTemplate jdbc;
  private OddsMarketRepository repository;
  private OddsForPredictionAdapter adapter;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUsername(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRESQL.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    repository = new OddsMarketRepository(jdbc, new DataSourceTransactionManager(dataSource));
    adapter = new OddsForPredictionAdapter(jdbc);
  }

  @Test
  @DisplayName("snapshot usa head atual e publicacao efetiva da ancestralidade")
  void resolvesNearestPublishedAncestorWithAuthoritativeMetadata() {
    SportsFixture sports = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID publishedVersionId = UUID.randomUUID();
    UUID openHeadId = UUID.randomUUID();
    UUID publicationId = UUID.randomUUID();
    Instant deadline = Instant.parse("2030-06-15T18:00:00Z");
    repository.saveMarket(
        new OddsMarketRecord(marketId, sports.eventId(), "H2H", null, 0, null, null));
    repository.saveVersion(
        new OddsMarketVersion(
            publishedVersionId, marketId, 1, MarketState.SUSPENDED, deadline, null, null));
    repository.saveVersion(
        new OddsMarketVersion(
            openHeadId, marketId, 2, MarketState.OPEN, deadline, publishedVersionId, null));
    assertThat(repository.advanceMarketHead(marketId, openHeadId, 0)).isTrue();
    UUID calculationId = UUID.randomUUID();
    repository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            publishedVersionId,
            "elo-davidson-authoritative-v9",
            "sha256:snapshot",
            "{\"publishedOdds\":[2.15,3.40,1.85]}",
            null,
            new BigDecimal("0.4650000000"),
            new BigDecimal("0.8000000000"),
            null,
            null));
    repository.savePublication(
        new OddsPublicationRecord(
            publicationId,
            calculationId,
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("2.15"),
            null,
            0,
            null));

    OddsForPredictionPort.MarketSnapshot snapshot = adapter.getSnapshot(marketId);

    assertThat(snapshot.marketId()).isEqualTo(marketId);
    assertThat(snapshot.eventId()).isEqualTo(sports.eventId());
    assertThat(snapshot.competitionId()).isEqualTo(sports.competitionId());
    assertThat(snapshot.state()).isEqualTo("OPEN");
    assertThat(snapshot.marketVersionId()).isEqualTo(openHeadId);
    assertThat(snapshot.publicationId()).isEqualTo(publicationId);
    assertThat(snapshot.ruleVersion()).isEqualTo("elo-davidson-authoritative-v9");
    assertThat(snapshot.closeDeadline()).isEqualTo(deadline);
    assertThat(snapshot.selections())
        .extracting(
            OddsForPredictionPort.Selection::key,
            OddsForPredictionPort.Selection::label,
            OddsForPredictionPort.Selection::odds)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("HOME", "Mandante", new BigDecimal("2.1500000000")),
            org.assertj.core.groups.Tuple.tuple("DRAW", "Empate", new BigDecimal("3.4000000000")),
            org.assertj.core.groups.Tuple.tuple(
                "AWAY", "Visitante", new BigDecimal("1.8500000000")));
  }

  @Test
  @DisplayName("snapshot falha fechado quando duas publicacoes sao igualmente efetivas")
  void rejectsAmbiguousNearestPublication() {
    SportsFixture sports = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    repository.saveMarket(
        new OddsMarketRecord(marketId, sports.eventId(), "H2H", null, 0, null, null));
    repository.saveVersion(
        new OddsMarketVersion(
            versionId,
            marketId,
            1,
            MarketState.OPEN,
            Instant.parse("2030-06-15T18:00:00Z"),
            null,
            null));
    assertThat(repository.advanceMarketHead(marketId, versionId, 0)).isTrue();
    createPublication(versionId, "elo-first", "{\"publishedOdds\":[2.10,1.90]}");
    createPublication(versionId, "elo-second", "{\"publishedOdds\":[2.20,1.80]}");

    assertThatThrownBy(() -> adapter.getSnapshot(marketId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("odds snapshot is unavailable");
  }

  private void createPublication(UUID versionId, String algorithm, String snapshot) {
    UUID calculationId = UUID.randomUUID();
    repository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            versionId,
            algorithm,
            "sha256:" + algorithm,
            snapshot,
            null,
            new BigDecimal("0.5000000000"),
            new BigDecimal("0.8000000000"),
            null,
            null));
    repository.savePublication(
        new OddsPublicationRecord(
            UUID.randomUUID(),
            calculationId,
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("2.10"),
            null,
            1,
            null));
  }

  private SportsFixture createSportsEvent() {
    UUID competitionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Snapshot Competition', ?) RETURNING competition_id",
            UUID.class,
            "snapshot-competition-" + UUID.randomUUID());
    UUID editionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Snapshot Edition', ?) RETURNING edition_id",
            UUID.class,
            competitionId,
            "snapshot-edition-" + UUID.randomUUID());
    UUID eventId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Snapshot Event', 'HEAD_TO_HEAD', CURRENT_TIMESTAMP + INTERVAL '1 day', 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://example.invalid/snapshot') RETURNING event_id",
            UUID.class,
            competitionId,
            editionId);
    return new SportsFixture(competitionId, eventId);
  }

  private record SportsFixture(UUID competitionId, UUID eventId) {}
}
