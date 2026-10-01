package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.betting.internal.PredictionForSettlementAdapter;
import br.com.betufu.settlement.SettlementModels.Authorization;
import br.com.betufu.settlement.SettlementModels.AuthorizationDecision;
import br.com.betufu.settlement.SettlementModels.SettlementCase;
import br.com.betufu.settlement.SettlementModels.SettlementReason;
import br.com.betufu.settlement.SettlementModels.Unit;
import br.com.betufu.settlement.internal.SettlementRepository;
import br.com.betufu.sports.internal.settlement.SportsResultForSettlementAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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
class SettlementSchemaIT {
  private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18.4-trixie");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_settlement")
          .withUsername("betufu_settlement")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private Fixture fixture;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    Flyway.configure().dataSource(source).load().migrate();
    jdbc.execute(
        "TRUNCATE TABLE public.local_identity, public.sports_competition, public.odds_market CASCADE");
    fixture = createFixture();
  }

  @Test
  void storesVersionedUnitAndProtectsAllHistoricalFacts() {
    SettlementRepository repository = new SettlementRepository(jdbc);
    UUID authorizationId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    repository.insertAuthorization(authorization(authorizationId, AuthorizationDecision.APPROVED));
    repository.insertCase(
        new SettlementCase(
            caseId,
            authorizationId,
            fixture.eventId,
            fixture.factId,
            1,
            "settlement-v1",
            Instant.now()));
    repository.insertUnit(
        new Unit(
            unitId,
            caseId,
            fixture.predictionId,
            fixture.identityId,
            SettlementState.WON,
            SettlementReason.CONFIRMED_RESULT,
            new BigDecimal("106.00"),
            null,
            "settlement-unit-1",
            Instant.now()));

    assertThat(repository.findUnitByKey("settlement-unit-1"))
        .get()
        .extracting(Unit::state)
        .isEqualTo(SettlementState.WON);
    assertThat(
            jdbc.queryForObject(
                "SELECT scale(amount) FROM public.prediction_settlement WHERE settlement_id = ?",
                Integer.class,
                unitId))
        .isEqualTo(2);
    assertThatThrownBy(
            () ->
                repository.insertUnit(
                    new Unit(
                        UUID.randomUUID(),
                        caseId,
                        fixture.predictionId,
                        fixture.identityId,
                        SettlementState.WON,
                        SettlementReason.CONFIRMED_RESULT,
                        new BigDecimal("106.00"),
                        null,
                        "settlement-unit-2",
                        Instant.now())))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.prediction_settlement SET amount = 1.00 WHERE settlement_id = ?",
                    unitId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM public.settlement_case WHERE case_id = ?", caseId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.settlement_authorization SET decision = 'REJECTED' WHERE authorization_id = ?",
                    authorizationId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Integer.class))
        .isZero();
  }

  @Test
  void exactConfirmedVersionAndApprovedDecisionAreRequired() {
    SettlementRepository repository = new SettlementRepository(jdbc);
    assertThatThrownBy(
            () ->
                repository.insertAuthorization(
                    new Authorization(
                        UUID.randomUUID(),
                        fixture.eventId,
                        fixture.factId,
                        UUID.randomUUID(),
                        fixture.identityId,
                        AuthorizationDecision.APPROVED,
                        Instant.now(),
                        "wrong-version")))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("exact confirmed version");
    UUID rejectedId = UUID.randomUUID();
    repository.insertAuthorization(authorization(rejectedId, AuthorizationDecision.REJECTED));
    assertThatThrownBy(
            () ->
                repository.insertCase(
                    new SettlementCase(
                        UUID.randomUUID(),
                        rejectedId,
                        fixture.eventId,
                        fixture.factId,
                        1,
                        "settlement-v1",
                        Instant.now())))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("approved authorization");
  }

  @Test
  void producerAdaptersReadCurrentOutcomeAndAcceptedTicket() {
    SportsResultForSettlementPort.ResultSnapshot result =
        new SportsResultForSettlementAdapter(jdbc).findCurrent(fixture.eventId).orElseThrow();
    assertThat(result.outcomeFactId()).isEqualTo(fixture.factId);
    assertThat(result.confirmedVersionId()).isEqualTo(fixture.versionId);
    assertThat(result.homeScore()).isEqualTo(2);
    assertThat(result.awayScore()).isEqualTo(1);
    assertThat(new PredictionForSettlementAdapter(jdbc).findAcceptedByEvent(fixture.eventId))
        .singleElement()
        .extracting(PredictionForSettlementPort.PredictionSnapshot::predictionId)
        .isEqualTo(fixture.predictionId);
    assertThat(
            new PredictionForSettlementAdapter(jdbc)
                .findAcceptedByEvent(fixture.eventId)
                .getFirst()
                .state())
        .isEqualTo(PredictionForSettlementPort.PredictionState.PENDING);
    UUID anotherIdentity =
        jdbc.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', 'annulled-fixture') RETURNING identity_id",
            UUID.class);
    UUID marketId =
        jdbc.queryForObject(
            "SELECT market_id FROM public.prediction WHERE prediction_id = ?",
            UUID.class,
            fixture.predictionId);
    jdbc.update(
        "INSERT INTO public.prediction (identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, state, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, 'elo-v1', 'AWAY', 50.00, 2.1200000000, 106.00, 56.00, CURRENT_TIMESTAMP, 'ANNULLED', 'annulled-1', 'digest-2')",
        anotherIdentity,
        marketId,
        UUID.randomUUID(),
        UUID.randomUUID());
    assertThat(new PredictionForSettlementAdapter(jdbc).findAcceptedByEvent(fixture.eventId))
        .extracting(PredictionForSettlementPort.PredictionSnapshot::state)
        .containsExactlyInAnyOrder(
            PredictionForSettlementPort.PredictionState.PENDING,
            PredictionForSettlementPort.PredictionState.ANNULLED);
  }

  private Authorization authorization(UUID id, AuthorizationDecision decision) {
    return new Authorization(
        id,
        fixture.eventId,
        fixture.factId,
        fixture.versionId,
        fixture.identityId,
        decision,
        Instant.now(),
        "auth-" + id);
  }

  private Fixture createFixture() {
    UUID identityId =
        jdbc.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', 'settlement-fixture') RETURNING identity_id",
            UUID.class);
    UUID competitionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Settlement', 'settlement') RETURNING competition_id",
            UUID.class);
    UUID editionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition', 'edition') RETURNING edition_id",
            UUID.class,
            competitionId);
    UUID eventId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Settlement event', 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid') RETURNING event_id",
            UUID.class,
            competitionId,
            editionId);
    UUID[] resultIds = new UUID[2];
    transactions.executeWithoutResult(
        ignored -> {
          UUID versionId =
              jdbc.queryForObject(
                  "INSERT INTO public.sports_result_version (event_id, competition_id, edition_id, version_no, status, source_reference, recorded_by_identity_id, confirmed_by_identity_id, confirmed_at, base_lock_version) VALUES (?, ?, ?, 1, 'CONFIRMADO', 'https://source.invalid/result', ?, ?, CURRENT_TIMESTAMP, 0) RETURNING version_id",
                  UUID.class,
                  eventId,
                  competitionId,
                  editionId,
                  identityId,
                  identityId);
          jdbc.update(
              "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 1, 2, 'Home'), (?, 2, 1, 'Away')",
              versionId,
              versionId);
          jdbc.update(
              "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
              versionId,
              eventId);
          UUID factId =
              jdbc.queryForObject(
                  "INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at) VALUES (?, ?, ?, 1, 'SPORTING_RESULT', 'https://source.invalid/result', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING fact_id",
                  UUID.class,
                  eventId,
                  competitionId,
                  editionId,
                  identityId);
          jdbc.update(
              "INSERT INTO public.sports_outcome_sporting_payload (fact_id, event_id, confirmed_version_id, confirmed_version_status, confirmed_at) VALUES (?, ?, ?, 'CONFIRMADO', CURRENT_TIMESTAMP)",
              factId,
              eventId,
              versionId);
          jdbc.update(
              "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)",
              eventId,
              factId);
          resultIds[0] = versionId;
          resultIds[1] = factId;
        });
    UUID marketId = UUID.randomUUID();
    UUID predictionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, 'H2H')",
        marketId,
        eventId);
    jdbc.update(
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, 'elo-v1', 'HOME', 50.00, 2.1200000000, 106.00, 56.00, CURRENT_TIMESTAMP, 'prediction-1', 'digest-1')",
        predictionId,
        identityId,
        marketId,
        UUID.randomUUID(),
        UUID.randomUUID());
    return new Fixture(identityId, eventId, resultIds[0], resultIds[1], predictionId);
  }

  private record Fixture(
      UUID identityId, UUID eventId, UUID versionId, UUID factId, UUID predictionId) {}
}
