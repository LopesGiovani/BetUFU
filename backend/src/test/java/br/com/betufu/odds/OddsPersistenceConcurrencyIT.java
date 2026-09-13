package br.com.betufu.odds;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsLifecycleRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.odds.internal.persistence.OddsOverrideRecord;
import br.com.betufu.odds.internal.persistence.OddsPublicationRecord;
import br.com.betufu.odds.internal.prediction.OddsForPredictionAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OddsPersistenceConcurrencyIT {

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_odds_concurrency_test")
          .withUsername("betufu_odds_concurrency_admin")
          .withPassword("betufu-odds-concurrency-pass");

  private JdbcTemplate jdbcTemplate;
  private DriverManagerDataSource dataSource;
  private OddsMarketRepository repository;

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource();
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
  @DisplayName("duas conexões concorrentes compartilham um único recibo idempotente")
  void concurrentRetryCreatesOneFact() throws Exception {
    UUID actor = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    OddsMarketRecord market =
        new OddsMarketRecord(marketId, UUID.randomUUID(), "H2H", null, 0, null, null);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<OddsMarketRecord>> futures =
          List.of(
              executor.submit(
                  () -> {
                    start.await(10, SECONDS);
                    return newRepository().saveMarket(market, actor, "same-command", "digest");
                  }),
              executor.submit(
                  () -> {
                    start.await(10, SECONDS);
                    return newRepository().saveMarket(market, actor, "same-command", "digest");
                  }));
      start.countDown();
      assertThat(futures.get(0).get(30, SECONDS).marketId()).isEqualTo(marketId);
      assertThat(futures.get(1).get(30, SECONDS).marketId()).isEqualTo(marketId);
      assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.odds_market", Long.class))
          .isEqualTo(1L);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM public.odds_idempotency_command", Long.class))
          .isEqualTo(1L);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("publicação, lifecycle e override são fatos consultáveis e imutáveis")
  void appendsPublicationLifecycleAndOverride() {
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    repository.saveMarket(
        new OddsMarketRecord(marketId, UUID.randomUUID(), "H2H", null, 0, null, null));
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'SUSPENDED')",
        versionId,
        marketId);
    repository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            versionId,
            "elo-v1",
            "digest",
            "{\"publishedOdds\":[2.10,1.90]}",
            null,
            new BigDecimal("0.5"),
            new BigDecimal("0.5"),
            null,
            null));
    OddsPublicationRecord publication =
        repository.savePublication(
            new OddsPublicationRecord(
                UUID.randomUUID(),
                calculationId,
                new BigDecimal("0.05"),
                "HALF_UP",
                new BigDecimal("2.10"),
                null,
                0,
                null));
    OddsLifecycleRecord lifecycle =
        repository.saveLifecycle(
            new OddsLifecycleRecord(
                UUID.randomUUID(),
                marketId,
                "SUSPENDED",
                "OPEN",
                "revisão concluída",
                UUID.randomUUID(),
                null));
    OddsOverrideRecord override =
        repository.saveOverride(
            new OddsOverrideRecord(
                UUID.randomUUID(),
                marketId,
                calculationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "evidência anexada",
                "PROPOSED",
                Instant.now()));

    assertThat(repository.findPublication(publication.publicationId())).contains(publication);
    assertThat(repository.findLifecycle(lifecycle.lifecycleFactId())).contains(lifecycle);
    assertThat(repository.findOverride(override.overrideId())).contains(override);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.odds_publication WHERE publication_id = ?",
                    publication.publicationId()))
        .hasMessageContaining("odds history is immutable");
  }

  @Test
  @DisplayName("snapshot mantem lock pessimista ate a transacao terminar")
  void snapshotLockSerializesMarketHeadChanges() throws Exception {
    UUID eventId = createSportsEvent();
    UUID marketId = UUID.randomUUID();
    UUID publishedVersionId = UUID.randomUUID();
    UUID nextVersionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    repository.saveMarket(
        new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null));
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state, close_deadline) VALUES (?, ?, 1, 'OPEN', CURRENT_TIMESTAMP + INTERVAL '1 hour')",
        publishedVersionId,
        marketId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state, close_deadline, supersedes_version_id) VALUES (?, ?, 2, 'SUSPENDED', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)",
        nextVersionId,
        marketId,
        publishedVersionId);
    assertThat(repository.advanceMarketHead(marketId, publishedVersionId, 0)).isTrue();
    repository.saveCalculation(
        new OddsCalculationRecord(
            calculationId,
            publishedVersionId,
            "elo-lock-v1",
            "sha256:lock",
            "{\"publishedOdds\":[2.10,1.90]}",
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

    CountDownLatch snapshotRead = new CountDownLatch(1);
    CountDownLatch releaseSnapshot = new CountDownLatch(1);
    CountDownLatch writerStarted = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<OddsForPredictionPort.MarketSnapshot> reader =
          executor.submit(
              () -> {
                JdbcTemplate readerJdbc = jdbcTemplateForNewConnection();
                TransactionTemplate transaction =
                    new TransactionTemplate(new DataSourceTransactionManager(readerJdbc.getDataSource()));
                return transaction.execute(
                    status -> {
                      OddsForPredictionPort.MarketSnapshot snapshot =
                          new OddsForPredictionAdapter(readerJdbc).getSnapshot(marketId);
                      snapshotRead.countDown();
                      try {
                        assertThat(releaseSnapshot.await(10, SECONDS)).isTrue();
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                      }
                      return snapshot;
                    });
              });
      assertThat(snapshotRead.await(10, SECONDS)).isTrue();
      Future<Boolean> writer =
          executor.submit(
              () -> {
                writerStarted.countDown();
                return newRepository().advanceMarketHead(marketId, nextVersionId, 1);
              });
      assertThat(writerStarted.await(10, SECONDS)).isTrue();
      Thread.sleep(250);
      assertThat(writer).isNotDone();

      releaseSnapshot.countDown();
      assertThat(reader.get(10, SECONDS).marketVersionId()).isEqualTo(publishedVersionId);
      assertThat(writer.get(10, SECONDS)).isTrue();
    } finally {
      releaseSnapshot.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private UUID createSportsEvent() {
    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Odds Lock', ?) RETURNING competition_id",
            UUID.class,
            "odds-lock-" + UUID.randomUUID());
    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition', ?) RETURNING edition_id",
            UUID.class,
            competitionId,
            "edition-" + UUID.randomUUID());
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Lock event', 'HEAD_TO_HEAD', CURRENT_TIMESTAMP + INTERVAL '1 day', 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://example.invalid/lock') RETURNING event_id",
        UUID.class,
        competitionId,
        editionId);
  }

  private JdbcTemplate jdbcTemplateForNewConnection() {
    DriverManagerDataSource connectionDataSource = new DriverManagerDataSource();
    connectionDataSource.setDriverClassName("org.postgresql.Driver");
    connectionDataSource.setUrl(POSTGRESQL.getJdbcUrl());
    connectionDataSource.setUsername(POSTGRESQL.getUsername());
    connectionDataSource.setPassword(POSTGRESQL.getPassword());
    return new JdbcTemplate(connectionDataSource);
  }

  private OddsMarketRepository newRepository() {
    JdbcTemplate connectionJdbc = jdbcTemplateForNewConnection();
    return new OddsMarketRepository(
        connectionJdbc, new DataSourceTransactionManager(connectionJdbc.getDataSource()));
  }
}
