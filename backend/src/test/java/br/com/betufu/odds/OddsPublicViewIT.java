package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.OddsMarketModels.OddsMarketVersion;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.odds.internal.persistence.OddsPublicationRecord;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsExplanation;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import br.com.betufu.odds.internal.publicview.OddsPublicViewRepository;
import br.com.betufu.odds.internal.publicview.OddsPublicViewService;
import br.com.betufu.platform.api.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OddsPublicViewIT {
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_odds_public_view_test")
          .withUsername("betufu_odds_public_view_admin")
          .withPassword("betufu-odds-public-view-pass");

  private JdbcTemplate jdbc;
  private OddsMarketRepository marketRepository;
  private OddsPublicViewRepository publicViewRepository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRESQL.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    marketRepository = new OddsMarketRepository(jdbc, new DataSourceTransactionManager(dataSource));
    publicViewRepository = new OddsPublicViewRepository(jdbc);
  }

  @Test
  void explanationUsesFairProbabilityAndMarginAsSeparatePublicFields() {
    PublicOddsExplanation explanation =
        new PublicOddsExplanation(
            "elo-davidson-v1",
            "sha256:test",
            new BigDecimal("0.5"),
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("0.2"),
            "LOW",
            0);
    assertThat(explanation.fairProbability()).isEqualByComparingTo("0.5");
    assertThat(explanation.margin()).isEqualByComparingTo("0.05");
  }

  @Test
  void publicViewPublishesCurrentVersionDeadlineAndCompleteOrderedOddsVector() {
    UUID eventId = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    UUID publicationId = UUID.randomUUID();
    Instant deadline = Instant.parse("2030-06-15T18:00:00Z");
    Instant publishedAt = Instant.parse("2026-09-14T12:00:00Z");

    marketRepository.saveMarket(
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            versionId, marketId, 1, MarketState.OPEN, deadline, null, publishedAt));
    assertThat(marketRepository.advanceMarketHead(marketId, versionId, 0)).isTrue();
    marketRepository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            versionId,
            "elo-davidson-v9",
            "sha256:public-view",
            "{\"publishedOdds\":[2.15,3.40,1.85],\"confidenceBand\":\"HIGH\",\"sampleSize\":12}",
            null,
            new BigDecimal("0.4650000000"),
            new BigDecimal("0.9000000000"),
            null,
            publishedAt));
    marketRepository.savePublication(
        new OddsPublicationRecord(
            publicationId,
            calculationId,
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("2.15"),
            null,
            0,
            publishedAt));

    PublicOddsView view = publicViewRepository.find(marketId).orElseThrow();

    assertThat(view.marketId()).isEqualTo(marketId);
    assertThat(view.marketVersionId()).isEqualTo(versionId);
    assertThat(view.marketType()).isEqualTo("HEAD_TO_HEAD");
    assertThat(view.closeDeadline()).isEqualTo(deadline);
    assertThat(view.current().publicationId()).isEqualTo(publicationId);
    assertThat(view.current().publishedOdds())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(
            new BigDecimal("2.1500000000"),
            new BigDecimal("3.4000000000"),
            new BigDecimal("1.8500000000"));
    assertThat(view.history())
        .singleElement()
        .satisfies(
            history -> {
              assertThat(history.publishedOdds()).isEqualByComparingTo("2.15");
            });
    assertThat(view.explanation().algorithmVersion()).isEqualTo("elo-davidson-v9");
  }

  @Test
  void publicViewIsUnavailableUntilPublicationAndDeadlineAreComplete() {
    UUID unpublishedMarket =
        createMarket(createSportsEvent(), Instant.parse("2030-06-15T18:00:00Z"), false);
    UUID missingDeadlineMarket = createMarket(createSportsEvent(), null, true);

    assertThat(publicViewRepository.find(unpublishedMarket)).isEmpty();
    assertThat(publicViewRepository.find(missingDeadlineMarket)).isEmpty();
    assertThatThrownBy(() -> new OddsPublicViewService(publicViewRepository).get(unpublishedMarket))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            error ->
                assertThat(((ApiProblemException) error).status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void publicViewUsesOnlyPublicationsFromTheCurrentVersionLineage() {
    UUID eventId = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID rootVersionId = UUID.randomUUID();
    UUID activeVersionId = UUID.randomUUID();
    UUID divergentVersionId = UUID.randomUUID();
    UUID authoritativePublicationId = UUID.randomUUID();
    UUID divergentPublicationId = UUID.randomUUID();
    Instant deadline = Instant.parse("2030-06-15T18:00:00Z");
    Instant rootPublishedAt = Instant.parse("2026-09-14T12:00:00Z");
    Instant divergentPublishedAt = Instant.parse("2026-09-14T13:00:00Z");

    marketRepository.saveMarket(
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            rootVersionId, marketId, 1, MarketState.OPEN, deadline, null, rootPublishedAt));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            activeVersionId,
            marketId,
            2,
            MarketState.OPEN,
            deadline,
            rootVersionId,
            rootPublishedAt.plusSeconds(60)));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            divergentVersionId,
            marketId,
            3,
            MarketState.OPEN,
            deadline,
            rootVersionId,
            divergentPublishedAt));
    assertThat(marketRepository.advanceMarketHead(marketId, activeVersionId, 0)).isTrue();

    savePublication(
        rootVersionId,
        authoritativePublicationId,
        "sha256:authoritative-lineage",
        new BigDecimal("2.15"),
        rootPublishedAt);
    savePublication(
        divergentVersionId,
        divergentPublicationId,
        "sha256:divergent-lineage",
        new BigDecimal("9.99"),
        divergentPublishedAt);

    PublicOddsView view = publicViewRepository.find(marketId).orElseThrow();

    assertThat(view.marketVersionId()).isEqualTo(activeVersionId);
    assertThat(view.current().publicationId()).isEqualTo(authoritativePublicationId);
    assertThat(view.history())
        .singleElement()
        .satisfies(
            history -> assertThat(history.publicationId()).isEqualTo(authoritativePublicationId));
  }

  @Test
  void publicViewFailsClosedWhenNearestVersionHasMultiplePublications() {
    UUID eventId = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    Instant deadline = Instant.parse("2030-06-15T18:00:00Z");
    Instant publishedAt = Instant.parse("2026-09-14T12:00:00Z");

    marketRepository.saveMarket(
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            versionId, marketId, 1, MarketState.OPEN, deadline, null, publishedAt));
    assertThat(marketRepository.advanceMarketHead(marketId, versionId, 0)).isTrue();
    savePublication(
        versionId,
        UUID.randomUUID(),
        "sha256:first-current-publication",
        new BigDecimal("2.15"),
        publishedAt);
    savePublication(
        versionId,
        UUID.randomUUID(),
        "sha256:second-current-publication",
        new BigDecimal("2.20"),
        publishedAt.plusSeconds(60));

    assertThat(publicViewRepository.find(marketId)).isEmpty();
  }

  private UUID createMarket(UUID eventId, Instant deadline, boolean published) {
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    Instant publishedAt = Instant.parse("2026-09-14T12:00:00Z");
    marketRepository.saveMarket(
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    marketRepository.saveVersion(
        new OddsMarketVersion(
            versionId, marketId, 1, MarketState.OPEN, deadline, null, publishedAt));
    assertThat(marketRepository.advanceMarketHead(marketId, versionId, 0)).isTrue();
    if (!published) return marketId;

    UUID calculationId = UUID.randomUUID();
    marketRepository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            versionId,
            "elo-davidson-v9",
            "sha256:incomplete-public-view",
            "{\"publishedOdds\":[2.15,1.85],\"confidenceBand\":\"HIGH\",\"sampleSize\":12}",
            null,
            new BigDecimal("0.4650000000"),
            new BigDecimal("0.9000000000"),
            null,
            publishedAt));
    marketRepository.savePublication(
        new OddsPublicationRecord(
            UUID.randomUUID(),
            calculationId,
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("2.15"),
            null,
            0,
            publishedAt));
    return marketId;
  }

  private void savePublication(
      UUID versionId, UUID publicationId, String digest, BigDecimal homeOdds, Instant publishedAt) {
    UUID calculationId = UUID.randomUUID();
    marketRepository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            versionId,
            "elo-davidson-v9",
            digest,
            "{\"publishedOdds\":["
                + homeOdds
                + ",1.85],\"confidenceBand\":\"HIGH\",\"sampleSize\":12}",
            null,
            new BigDecimal("0.4650000000"),
            new BigDecimal("0.9000000000"),
            null,
            publishedAt));
    marketRepository.savePublication(
        new OddsPublicationRecord(
            publicationId,
            calculationId,
            new BigDecimal("0.05"),
            "HALF_UP",
            homeOdds,
            null,
            0,
            publishedAt));
  }

  private UUID createSportsEvent() {
    UUID competitionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Public View Competition', ?) RETURNING competition_id",
            UUID.class,
            "public-view-competition-" + UUID.randomUUID());
    UUID editionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Public View Edition', ?) RETURNING edition_id",
            UUID.class,
            competitionId,
            "public-view-edition-" + UUID.randomUUID());
    return jdbc.queryForObject(
        "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Public View Event', 'HEAD_TO_HEAD', CURRENT_TIMESTAMP + INTERVAL '1 day', 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://example.invalid/public-view') RETURNING event_id",
        UUID.class,
        competitionId,
        editionId);
  }
}
