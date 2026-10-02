package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.identity.internal.RankingNicknameAdapter;
import br.com.betufu.ranking.internal.RankingRebuildService;
import br.com.betufu.ranking.internal.RankingRepository;
import br.com.betufu.ranking.internal.RankingService;
import br.com.betufu.settlement.internal.RankingSettlementAdapter;
import br.com.betufu.verification.internal.RankingEnrollmentAdapter;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RankingSchemaIT extends RankingFixture {
  @Test
  void v36CreatesOnlyRebuildableProjectionAndLeavesFactsImmutable() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.ranking_projection", Long.class))
        .isZero();
    UUID person = identity("aluno", true);
    DecisionFixture first = decision(person, "WON");
    service.findLifetimeRanking();
    assertThat(jdbc.queryForObject("SELECT wins FROM public.ranking_projection", Long.class))
        .isEqualTo(1);
    assertThatThrownBy(() -> jdbc.update("UPDATE public.prediction_settlement SET state = 'LOST'"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbc.update("UPDATE public.prediction SET selection = 'AWAY'"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Long.class))
        .isEqualTo(1);
    assertThat(first.predictionId()).isNotNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class))
        .isZero();
  }
}

abstract class RankingFixture {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_ranking")
          .withUsername("betufu_ranking")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  protected JdbcTemplate jdbc;
  protected RankingRepository repository;
  protected RankingService service;

  @BeforeEach
  void setUpRanking() {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).load().migrate();
    jdbc.execute(
        "TRUNCATE TABLE public.local_identity, public.sports_competition, public.odds_market CASCADE");
    repository = new RankingRepository(jdbc);
    RankingEnrollmentAdapter enrollment = new RankingEnrollmentAdapter(jdbc);
    service =
        new RankingService(
            new RankingRebuildService(
                repository,
                new RankingSettlementAdapter(jdbc),
                enrollment,
                new DataSourceTransactionManager(source)),
            repository,
            enrollment,
            new RankingNicknameAdapter(jdbc));
  }

  protected UUID identity(String nickname, boolean enrolledBeforeBet) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        id,
        id.toString());
    jdbc.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, nickname);
    if (enrolledBeforeBet) enroll(id, Instant.now().minusSeconds(86400));
    return id;
  }

  protected void enroll(UUID identityId, Instant createdAt) {
    byte[] digest =
        ByteBuffer.allocate(32)
            .putLong(identityId.getMostSignificantBits())
            .putLong(identityId.getLeastSignificantBits())
            .array();
    jdbc.update(
        "INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status, created_at, updated_at) VALUES (?, ?, 1, 'ATIVO', ?, ?)",
        identityId,
        digest,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
  }

  protected DecisionFixture decision(UUID person, String state) {
    UUID competition =
        jdbc.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
            UUID.class,
            "Ranking " + UUID.randomUUID(),
            "ranking-" + UUID.randomUUID());
    UUID edition =
        jdbc.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition', 'edition') RETURNING edition_id",
            UUID.class,
            competition);
    UUID event =
        jdbc.queryForObject(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Ranking event', 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid') RETURNING event_id",
            UUID.class,
            competition,
            edition);
    UUID fact =
        jdbc.queryForObject(
            "INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at) VALUES (?, ?, ?, 1, 'ADMINISTRATIVE_WALKOVER', 'https://source.invalid/result', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING fact_id",
            UUID.class,
            event,
            competition,
            edition,
            person);
    jdbc.update(
        "INSERT INTO public.sports_outcome_administrative_payload (fact_id, event_id, administrative_reason, winner_slot_no) VALUES (?, ?, 'W.O.', 1)",
        fact,
        event);
    jdbc.update(
        "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)", event, fact);
    UUID market = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, 'H2H')",
        market,
        event);
    UUID prediction = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest, accepted_at) VALUES (?, ?, ?, ?, ?, 'elo-v1', 'HOME', 50.00, 2.1200000000, 106.00, 56.00, CURRENT_TIMESTAMP, ?, ?, ?)",
        prediction,
        person,
        market,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "prediction-" + prediction,
        "digest-" + prediction,
        Timestamp.from(Instant.now().minusSeconds(3600)));
    UUID authorization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.settlement_authorization (authorization_id, event_id, outcome_fact_id, authorized_by_identity_id, decision, authorized_at, idempotency_key) VALUES (?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, ?)",
        authorization,
        event,
        fact,
        person,
        "authorization-" + authorization);
    UUID caseId = insertCase(authorization, event, fact, 1, true);
    insertUnit(caseId, prediction, person, state, null);
    return new DecisionFixture(person, event, fact, authorization, market, prediction, caseId);
  }

  protected UUID insertCase(
      UUID authorization, UUID event, UUID fact, int version, boolean complete) {
    UUID caseId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.settlement_case (case_id, authorization_id, event_id, outcome_fact_id, processing_version, rule_version) VALUES (?, ?, ?, ?, ?, 'ranking-test-v1')",
        caseId,
        authorization,
        event,
        fact,
        version);
    jdbc.update(
        "INSERT INTO public.settlement_processing (case_id, status, total_units, completed_units) VALUES (?, ?, 1, ?)",
        caseId,
        complete ? "COMPLETE" : "PENDING",
        complete ? 1 : 0);
    return caseId;
  }

  protected UUID correctedCase(UUID event, int version, boolean complete) {
    UUID previous =
        jdbc.queryForObject(
            "SELECT fact_id FROM public.sports_outcome_head WHERE event_id = ?", UUID.class, event);
    UUID fact =
        jdbc.queryForObject(
            "INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) SELECT ?, competition_id, edition_id, ?, 'ADMINISTRATIVE_WALKOVER', 'https://source.invalid/correction', recorded_by_identity_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ? FROM public.sports_outcome_fact WHERE fact_id = ? RETURNING fact_id",
            UUID.class,
            event,
            version,
            previous,
            previous);
    jdbc.update(
        "INSERT INTO public.sports_outcome_administrative_payload (fact_id, event_id, administrative_reason, winner_slot_no) VALUES (?, ?, 'corrected W.O.', 2)",
        fact,
        event);
    jdbc.update(
        "UPDATE public.sports_outcome_head SET fact_id = ?, lock_version = lock_version + 1 WHERE event_id = ?",
        fact,
        event);
    UUID authorization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.settlement_authorization (authorization_id, event_id, outcome_fact_id, authorized_by_identity_id, decision, authorized_at, idempotency_key) SELECT ?, ?, ?, recorded_by_identity_id, 'APPROVED', CURRENT_TIMESTAMP, ? FROM public.sports_outcome_fact WHERE fact_id = ?",
        authorization,
        event,
        fact,
        "authorization-" + authorization,
        fact);
    return insertCase(authorization, event, fact, version, complete);
  }

  protected void insertUnit(
      UUID caseId, UUID prediction, UUID person, String state, UUID supersedes) {
    UUID unit = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.prediction_settlement (settlement_id, case_id, prediction_id, identity_id, state, reason, amount, supersedes_settlement_id, idempotency_key) VALUES (?, ?, ?, ?, ?, 'CONFIRMED_RESULT', 0.00, ?, ?)",
        unit,
        caseId,
        prediction,
        person,
        state,
        supersedes,
        "unit-" + unit);
  }

  protected record DecisionFixture(
      UUID person,
      UUID event,
      UUID fact,
      UUID authorization,
      UUID market,
      UUID predictionId,
      UUID caseId) {}
}
