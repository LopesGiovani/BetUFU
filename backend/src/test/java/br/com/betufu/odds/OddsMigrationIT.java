package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.prediction.OddsForPredictionAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
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
  private DriverManagerDataSource dataSource;
  private TransactionTemplate transactions;

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUsername(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());
    jdbcTemplate = new JdbcTemplate(dataSource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
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
    assertThat(result.migrationsExecuted).isEqualTo(38);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('odds_market', 'odds_market_version', 'odds_calculation', 'odds_publication', 'odds_publication_selection', 'odds_lifecycle', 'odds_override', 'odds_audit_event', 'odds_idempotency_command')",
                Integer.class))
        .isEqualTo(9);
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
        "{\"source\":\"fixture\",\"publishedOdds\":[2.10,1.90]}",
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

  @Test
  @DisplayName("V31 faz backfill 2-way e 3-way preservando HOME legado de override")
  void backfillsCompleteLegacyVectors() {
    migrateTo30();
    SportsFixture twoWaySports = createSportsEvent("two-way");
    LegacyPublication twoWay =
        insertLegacyPublication(
            twoWaySports.eventId(), "{\"publishedOdds\":[2.10,1.90]}", new BigDecimal("2.35"));
    jdbcTemplate.update(
        "INSERT INTO public.odds_override (market_id, calculation_id, proposer_id, approver_id, reason, decision) VALUES (?, ?, ?, ?, 'legacy override', 'APPROVED')",
        twoWay.marketId(),
        twoWay.calculationId(),
        UUID.randomUUID(),
        UUID.randomUUID());
    SportsFixture threeWaySports = createSportsEvent("three-way");
    LegacyPublication threeWay =
        insertLegacyPublication(
            threeWaySports.eventId(),
            "{\"publishedOdds\":[1.95,3.40,2.75]}",
            new BigDecimal("1.95"));

    assertThat(
            Flyway.configure()
                .dataSource(dataSource)
                .target("31")
                .load()
                .migrate()
                .migrationsExecuted)
        .isEqualTo(1);

    assertSelectionVector(twoWay.publicationId(), List.of("HOME", "AWAY"), List.of("2.35", "1.90"));
    assertSelectionVector(
        threeWay.publicationId(), List.of("HOME", "DRAW", "AWAY"), List.of("1.95", "3.40", "2.75"));
    OddsForPredictionPort.MarketSnapshot migrated =
        new OddsForPredictionAdapter(jdbcTemplate).getSnapshot(twoWay.marketId());
    assertThat(migrated.competitionId()).isEqualTo(twoWaySports.competitionId());
    assertThat(migrated.marketVersionId()).isEqualTo(twoWay.headVersionId());
    assertThat(migrated.publicationId()).isEqualTo(twoWay.publicationId());
    assertThat(migrated.ruleVersion()).isEqualTo("elo-upgrade-v1");
    assertThat(migrated.selections())
        .extracting(OddsForPredictionPort.Selection::key, OddsForPredictionPort.Selection::odds)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("HOME", new BigDecimal("2.3500000000")),
            org.assertj.core.groups.Tuple.tuple("AWAY", new BigDecimal("1.9000000000")));
  }

  @Test
  @DisplayName("V31 aborta por inteiro quando publicacao legada nao possui vetor valido")
  void rejectsUnsafeLegacyBackfill() {
    migrateTo30();
    insertLegacyPublication(
        UUID.randomUUID(), "{\"publishedOdds\":[2.10]}", new BigDecimal("2.10"));

    assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
        .hasMessageContaining("legacy odds publication cannot be migrated safely");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'odds_publication_selection'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "V31 rejeita no commit vetores ausentes, parciais, excedentes, duplicados ou fora de ordem")
  void rejectsInvalidCommittedVectors() {
    Flyway.configure().dataSource(dataSource).load().migrate();
    LegacyPublication fixture = insertLegacyPublicationAfterV31("{\"publishedOdds\":[2.10,1.90]}");

    assertInvalidVector(fixture.calculationId(), List.of());
    assertInvalidVector(fixture.calculationId(), List.of(new SelectionRow(1, "HOME", "2.10")));
    assertInvalidVector(
        fixture.calculationId(),
        List.of(
            new SelectionRow(1, "HOME", "2.10"),
            new SelectionRow(2, "AWAY", "1.90"),
            new SelectionRow(3, "DRAW", "3.00"),
            new SelectionRow(4, "HOME", "2.10")));
    assertInvalidVector(
        fixture.calculationId(),
        List.of(new SelectionRow(1, "HOME", "2.10"), new SelectionRow(2, "HOME", "1.90")));
    assertInvalidVector(
        fixture.calculationId(),
        List.of(new SelectionRow(1, "AWAY", "1.90"), new SelectionRow(2, "HOME", "2.10")));
  }

  private void migrateTo30() {
    assertThat(Flyway.configure().dataSource(dataSource).target("30").load().migrate().success)
        .isTrue();
  }

  private LegacyPublication insertLegacyPublication(
      UUID eventId, String snapshot, BigDecimal homeOdds) {
    UUID marketId = UUID.randomUUID();
    UUID publishedVersionId = UUID.randomUUID();
    UUID headVersionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    UUID publicationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, 'H2H')",
        marketId,
        eventId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'SUSPENDED')",
        publishedVersionId,
        marketId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state, supersedes_version_id) VALUES (?, ?, 2, 'OPEN', ?)",
        headVersionId,
        marketId,
        publishedVersionId);
    jdbcTemplate.update(
        "UPDATE public.odds_market SET current_version_id = ?, lock_version = 1 WHERE market_id = ?",
        headVersionId,
        marketId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_calculation (calculation_id, market_version_id, algorithm_version, configuration_digest, input_snapshot, fair_probability) VALUES (?, ?, 'elo-upgrade-v1', 'sha256:upgrade', ?::jsonb, 0.5)",
        calculationId,
        publishedVersionId,
        snapshot);
    jdbcTemplate.update(
        "INSERT INTO public.odds_publication (publication_id, calculation_id, margin, rounding_mode, published_odds) VALUES (?, ?, 0.05, 'HALF_UP', ?)",
        publicationId,
        calculationId,
        homeOdds);
    return new LegacyPublication(marketId, headVersionId, calculationId, publicationId);
  }

  private LegacyPublication insertLegacyPublicationAfterV31(String snapshot) {
    UUID marketId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, 'H2H')",
        marketId,
        UUID.randomUUID());
    jdbcTemplate.update(
        "INSERT INTO public.odds_market_version (market_version_id, market_id, version_no, state) VALUES (?, ?, 1, 'OPEN')",
        versionId,
        marketId);
    jdbcTemplate.update(
        "UPDATE public.odds_market SET current_version_id = ? WHERE market_id = ?",
        versionId,
        marketId);
    jdbcTemplate.update(
        "INSERT INTO public.odds_calculation (calculation_id, market_version_id, algorithm_version, configuration_digest, input_snapshot, fair_probability) VALUES (?, ?, 'elo-vector-v1', 'sha256:vector', ?::jsonb, 0.5)",
        calculationId,
        versionId,
        snapshot);
    return new LegacyPublication(marketId, versionId, calculationId, null);
  }

  private SportsFixture createSportsEvent(String suffix) {
    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
            UUID.class,
            "Upgrade " + suffix,
            "upgrade-" + suffix + "-" + UUID.randomUUID());
    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, ?, ?) RETURNING edition_id",
            UUID.class,
            competitionId,
            "Edition " + suffix,
            "edition-" + suffix + "-" + UUID.randomUUID());
    UUID eventId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, ?, 'HEAD_TO_HEAD', CURRENT_TIMESTAMP + INTERVAL '1 day', 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', ?) RETURNING event_id",
            UUID.class,
            competitionId,
            editionId,
            "Event " + suffix,
            "https://example.invalid/upgrade/" + suffix);
    return new SportsFixture(competitionId, eventId);
  }

  private void assertInvalidVector(UUID calculationId, List<SelectionRow> rows) {
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      UUID publicationId = UUID.randomUUID();
                      jdbcTemplate.update(
                          "INSERT INTO public.odds_publication (publication_id, calculation_id, margin, rounding_mode, published_odds) VALUES (?, ?, 0.05, 'HALF_UP', 2.10)",
                          publicationId,
                          calculationId);
                      for (SelectionRow row : rows) {
                        jdbcTemplate.update(
                            "INSERT INTO public.odds_publication_selection (publication_id, selection_no, selection_key, published_odds) VALUES (?, ?, ?, ?)",
                            publicationId,
                            row.selectionNo(),
                            row.selectionKey(),
                            new BigDecimal(row.odds()));
                      }
                    }))
        .rootCause()
        .hasMessageContaining("odds publication selection vector is incomplete or invalid");
  }

  private void assertSelectionVector(
      UUID publicationId, List<String> expectedKeys, List<String> expectedOdds) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT selection_key, published_odds FROM public.odds_publication_selection WHERE publication_id = ? ORDER BY selection_no",
            publicationId);
    assertThat(rows)
        .extracting(row -> row.get("selection_key"))
        .containsExactlyElementsOf(expectedKeys);
    assertThat(rows)
        .extracting(row -> (BigDecimal) row.get("published_odds"))
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactlyElementsOf(expectedOdds.stream().map(BigDecimal::new).toList());
  }

  private record LegacyPublication(
      UUID marketId, UUID headVersionId, UUID calculationId, UUID publicationId) {}

  private record SportsFixture(UUID competitionId, UUID eventId) {}

  private record SelectionRow(int selectionNo, String selectionKey, String odds) {}
}
