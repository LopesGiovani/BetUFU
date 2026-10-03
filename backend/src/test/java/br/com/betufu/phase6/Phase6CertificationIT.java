package br.com.betufu.phase6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.betting.internal.PredictionCorrectionEvidenceAdapter;
import br.com.betufu.betting.internal.PredictionForSettlementAdapter;
import br.com.betufu.identity.internal.RankingNicknameAdapter;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditCorrectionRepository;
import br.com.betufu.ledger.internal.CreditCorrectionService;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.ledger.internal.LedgerCorrectionEvidenceAdapter;
import br.com.betufu.ledger.internal.SettlementCorrectionAdapter;
import br.com.betufu.ledger.internal.SettlementCreditAdapter;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.ranking.internal.AchievementRepository;
import br.com.betufu.ranking.internal.AchievementService;
import br.com.betufu.ranking.internal.RankingRebuildService;
import br.com.betufu.ranking.internal.RankingRepository;
import br.com.betufu.ranking.internal.RankingService;
import br.com.betufu.settlement.PredictionForSettlementPort;
import br.com.betufu.settlement.SettlementCorrectionPort;
import br.com.betufu.settlement.SettlementCreditPort;
import br.com.betufu.settlement.SportsResultForSettlementPort;
import br.com.betufu.settlement.internal.AchievementSettlementAdapter;
import br.com.betufu.settlement.internal.RankingSettlementAdapter;
import br.com.betufu.settlement.internal.SettlementCorrectionEvidenceAdapter;
import br.com.betufu.settlement.internal.SettlementRepository;
import br.com.betufu.settlement.internal.SettlementService;
import br.com.betufu.sports.internal.event.SportsEventOrganizationLookupService;
import br.com.betufu.sports.internal.settlement.SportsResultForSettlementAdapter;
import br.com.betufu.verification.internal.EnrollmentCorrectionEvidenceAdapter;
import br.com.betufu.verification.internal.RankingEnrollmentAdapter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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
class Phase6CertificationIT extends Phase6Fixture {
  @Test
  void exactAuthorizationRetryAndCancellationRebuildPreserveHistoricalFacts() {
    UUID original = authorize("phase6-original");
    assertThat(authorize("phase6-original")).isEqualTo(original);
    UUID originalCase = service.process(original);
    assertThat(service.process(original)).isEqualTo(originalCase);
    assertThat(ranking.findLifetimeRanking())
        .singleElement()
        .satisfies(entry -> assertThat(entry.wins()).isEqualTo(1));
    UUID oldUnit = service.findLatestByPrediction(predictionId).orElseThrow().settlementId();
    cancelEvent();
    assertThatThrownBy(() -> service.process(original)).isInstanceOf(IllegalStateException.class);
    UUID corrected = authorize("phase6-cancellation");
    UUID correctedCase = service.process(corrected);
    assertThat(service.process(corrected)).isEqualTo(correctedCase);
    assertThat(ranking.findLifetimeRanking()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM public.prediction_settlement WHERE settlement_id = ?",
                String.class,
                oldUnit))
        .isEqualTo("WON");
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1000.00");
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
    assertThat(ledgerEntries("REFUND")).isEqualTo(1);
    assertThat(ledgerEntries("CORRECTION_RECOVERY")).isEqualTo(1);
    assertFinancialIntegrity();
  }

  @Test
  void staleOrMissingExactVersionAndNonAdministratorCannotAuthorize() {
    assertThatThrownBy(
            () ->
                service.authorize(eventId, resultFactId, null, administratorId, "missing-version"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                service.authorize(
                    eventId, resultFactId, UUID.randomUUID(), administratorId, "wrong-version"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> service.authorize(eventId, resultFactId, resultVersionId, bettorId, "forbidden"))
        .isInstanceOf(SecurityException.class);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.settlement_authorization", Long.class))
        .isZero();
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("950.00");
  }
}

abstract class Phase6Fixture {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_phase6_certification")
          .withUsername("betufu_phase6_certification")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  protected JdbcTemplate jdbc;
  protected DataSourceTransactionManager manager;
  protected SettlementRepository repository;
  protected CreditLedgerPort ledger;
  protected SettlementCreditPort credits;
  protected SettlementCorrectionPort corrections;
  protected SettlementService service;
  protected SportsResultForSettlementPort sports;
  protected PredictionForSettlementPort predictions;
  protected OperationalIdentityLookup identities;
  protected Set<UUID> administrators;
  protected UUID administratorId;
  protected UUID bettorId;
  protected UUID competitionId;
  protected UUID editionId;
  protected UUID eventId;
  protected UUID marketId;
  protected UUID predictionId;
  protected UUID resultVersionId;
  protected UUID resultFactId;

  @BeforeEach
  void setUpSettlement() {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    manager = new DataSourceTransactionManager(source);
    Flyway.configure().dataSource(source).load().migrate();
    jdbc.execute(
        "TRUNCATE TABLE public.local_identity, public.sports_competition, public.odds_market CASCADE");
    repository = new SettlementRepository(jdbc);
    ledger = new CreditLedgerService(new CreditLedgerRepository(jdbc, manager));
    credits = new SettlementCreditAdapter(ledger);
    sports = new SportsResultForSettlementAdapter(jdbc);
    predictions = new PredictionForSettlementAdapter(jdbc);
    administrators = ConcurrentHashMap.newKeySet();
    identities =
        new OperationalIdentityLookup() {
          @Override
          public Optional<OperationalIdentity> lookupIdentity(String issuer, String subject) {
            return jdbc.query(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                rs ->
                    rs.next()
                        ? Optional.of(
                            new OperationalIdentity(rs.getObject(1, UUID.class), Set.of()))
                        : Optional.empty(),
                issuer,
                subject);
          }

          @Override
          public boolean hasGlobalRole(UUID identityId, String roleName) {
            return "ADMINISTRADOR_GERAL".equals(roleName) && administrators.contains(identityId);
          }
        };
    CreditCorrectionService correctionService =
        new CreditCorrectionService(
            new CreditCorrectionRepository(jdbc),
            ledger,
            identities,
            entry -> {},
            List.of(
                new LedgerCorrectionEvidenceAdapter(jdbc),
                new PredictionCorrectionEvidenceAdapter(jdbc),
                new SettlementCorrectionEvidenceAdapter(jdbc)),
            new EnrollmentCorrectionEvidenceAdapter(jdbc),
            manager);
    corrections = new SettlementCorrectionAdapter(correctionService);
    service = settlementService(credits, Clock.systemUTC(), Duration.ofMinutes(2));
    createScenario();
    configureProjections();
  }

  protected SettlementService settlementService(
      SettlementCreditPort creditPort, Clock clock, Duration lease) {
    return new SettlementService(
        repository,
        creditPort,
        sports,
        predictions,
        identities,
        manager,
        clock,
        lease,
        corrections);
  }

  protected int ledgerEntries(String entryType) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = ?",
        Integer.class,
        entryType);
  }

  protected UUID addPrediction(String selection) {
    UUID identity = createIdentity();
    UUID id = UUID.randomUUID();
    ledger.ensureInitialGrant(identity);
    ledger.debit(identity, new BigDecimal("50.00"), id);
    jdbc.update(
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, 'elo-v1', ?, 50.00, 3.0000000000, 150.00, 100.00, CURRENT_TIMESTAMP, ?, ?)",
        id,
        identity,
        marketId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        selection,
        "prediction-" + id,
        "digest-" + id);
    return id;
  }

  protected void cancelEvent() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            ignored -> {
              jdbc.update(
                  "UPDATE public.sports_event SET status = 'CANCELADO' WHERE event_id = ?",
                  eventId);
              UUID factId = UUID.randomUUID();
              jdbc.update(
                  "INSERT INTO public.sports_outcome_fact (fact_id, event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, reason, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, ?, 2, 'EVENT_CANCELLED', 'https://source.invalid/cancel', 'cancelled', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
                  factId,
                  eventId,
                  competitionId,
                  editionId,
                  administratorId,
                  resultFactId);
              jdbc.update(
                  "INSERT INTO public.sports_outcome_administrative_payload (fact_id, event_id, administrative_reason) VALUES (?, ?, 'cancelled')",
                  factId,
                  eventId);
              jdbc.update(
                  "UPDATE public.sports_outcome_head SET fact_id = ? WHERE event_id = ?",
                  factId,
                  eventId);
            });
  }

  private void createScenario() {
    administratorId = createIdentity();
    bettorId = createIdentity();
    administrators.add(administratorId);
    competitionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Settlement engine', 'settlement-engine') RETURNING competition_id",
            UUID.class);
    editionId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition', 'edition') RETURNING edition_id",
            UUID.class,
            competitionId);
    eventId =
        jdbc.queryForObject(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Settlement event', 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid') RETURNING event_id",
            UUID.class,
            competitionId,
            editionId);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            ignored -> {
              resultVersionId =
                  jdbc.queryForObject(
                      "INSERT INTO public.sports_result_version (event_id, competition_id, edition_id, version_no, status, source_reference, recorded_by_identity_id, confirmed_by_identity_id, confirmed_at, base_lock_version) VALUES (?, ?, ?, 1, 'CONFIRMADO', 'https://source.invalid/result', ?, ?, CURRENT_TIMESTAMP, 0) RETURNING version_id",
                      UUID.class,
                      eventId,
                      competitionId,
                      editionId,
                      administratorId,
                      administratorId);
              jdbc.update(
                  "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 1, 2, 'Home'), (?, 2, 1, 'Away')",
                  resultVersionId,
                  resultVersionId);
              jdbc.update(
                  "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
                  resultVersionId,
                  eventId);
              resultFactId =
                  jdbc.queryForObject(
                      "INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at) VALUES (?, ?, ?, 1, 'SPORTING_RESULT', 'https://source.invalid/result', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING fact_id",
                      UUID.class,
                      eventId,
                      competitionId,
                      editionId,
                      administratorId);
              jdbc.update(
                  "INSERT INTO public.sports_outcome_sporting_payload (fact_id, event_id, confirmed_version_id, confirmed_version_status, confirmed_at) VALUES (?, ?, ?, 'CONFIRMADO', CURRENT_TIMESTAMP)",
                  resultFactId,
                  eventId,
                  resultVersionId);
              jdbc.update(
                  "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)",
                  eventId,
                  resultFactId);
            });
    marketId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.odds_market (market_id, event_id, market_type) VALUES (?, ?, 'H2H')",
        marketId,
        eventId);
    ledger.ensureInitialGrant(bettorId);
    predictionId = UUID.randomUUID();
    ledger.debit(bettorId, new BigDecimal("50.00"), predictionId);
    jdbc.update(
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, 'elo-v1', 'HOME', 50.00, 3.0000000000, 150.00, 100.00, CURRENT_TIMESTAMP, 'prediction-main', 'digest-main')",
        predictionId,
        bettorId,
        marketId,
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  protected UUID createIdentity() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        id,
        id.toString());
    return id;
  }

  protected RankingService ranking;
  protected AchievementService achievements;

  private void configureProjections() {
    jdbc.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, 'certification-player')",
        bettorId);
    byte[] digest =
        ByteBuffer.allocate(32)
            .putLong(bettorId.getMostSignificantBits())
            .putLong(bettorId.getLeastSignificantBits())
            .array();
    jdbc.update(
        "INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status, created_at, updated_at) VALUES (?, ?, 1, 'ATIVO', ?, ?)",
        bettorId,
        digest,
        Timestamp.from(Instant.now().minusSeconds(86400)),
        Timestamp.from(Instant.now().minusSeconds(86400)));
    RankingEnrollmentAdapter eligibility = new RankingEnrollmentAdapter(jdbc);
    RankingRepository projection = new RankingRepository(jdbc);
    ranking =
        new RankingService(
            new RankingRebuildService(
                projection, new RankingSettlementAdapter(jdbc), eligibility, manager),
            projection,
            eligibility,
            new RankingNicknameAdapter(jdbc));
    achievements =
        new AchievementService(
            new AchievementSettlementAdapter(service),
            ranking,
            eligibility,
            identity ->
                jdbc.query(
                    "SELECT favorite_organization_id FROM public.betufu_profile WHERE identity_id = ?",
                    rs ->
                        rs.next()
                            ? Optional.ofNullable(rs.getObject(1, UUID.class))
                            : Optional.empty(),
                    identity),
            new SportsEventOrganizationLookupService(jdbc),
            new AchievementRepository(jdbc));
  }

  protected void evaluateAchievements() {
    new TransactionTemplate(manager).executeWithoutResult(ignored -> achievements.reconcile());
  }

  protected UUID authorize(String key) {
    var current = sports.findCurrent(eventId).orElseThrow();
    return service.authorize(
        eventId, current.outcomeFactId(), current.confirmedVersionId(), administratorId, key);
  }

  protected void assertFinancialIntegrity() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_account WHERE balance < 0", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT identity_id, entry_type, source_id FROM public.credit_ledger_entry GROUP BY identity_id, entry_type, source_id HAVING count(*) > 1) duplicates",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT case_id, prediction_id FROM public.prediction_settlement GROUP BY case_id, prediction_id HAVING count(*) > 1) duplicates",
                Long.class))
        .isZero();
  }
}
