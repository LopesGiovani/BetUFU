package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.betting.internal.PredictionCorrectionEvidenceAdapter;
import br.com.betufu.betting.internal.PredictionForSettlementAdapter;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditCorrectionRepository;
import br.com.betufu.ledger.internal.CreditCorrectionService;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.ledger.internal.LedgerCorrectionEvidenceAdapter;
import br.com.betufu.ledger.internal.SettlementCorrectionAdapter;
import br.com.betufu.ledger.internal.SettlementCreditAdapter;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.settlement.internal.SettlementCorrectionEvidenceAdapter;
import br.com.betufu.settlement.internal.SettlementRepository;
import br.com.betufu.settlement.internal.SettlementService;
import br.com.betufu.sports.internal.settlement.SportsResultForSettlementAdapter;
import br.com.betufu.verification.internal.EnrollmentCorrectionEvidenceAdapter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
class SettlementExecutionIT extends SettlementFixture {
  @Test
  void settlesWinnerOnceAndExposesAuditableCalculation() {
    UUID authorization = service.authorize(eventId, administratorId, "auth-win");
    UUID caseId = service.process(authorization);
    assertThat(service.process(authorization)).isEqualTo(caseId);
    assertThat(service.findCase(caseId).orElseThrow().status())
        .isEqualTo(SettlementReadPort.ProcessingStatus.COMPLETE);
    SettlementReadPort.PredictionOutcome outcome =
        service.findLatestByPrediction(predictionId).orElseThrow();
    assertThat(outcome.state()).isEqualTo(SettlementState.WON);
    assertThat(outcome.amount()).isEqualByComparingTo("106.00");
    assertThat(outcome.stake()).isEqualByComparingTo("50.00");
    assertThat(outcome.acceptedOdds()).isEqualByComparingTo("2.12");
    assertThat(outcome.homeScore()).isEqualTo(2);
    assertThat(outcome.awayScore()).isEqualTo(1);
    assertThat(outcome.ledgerEntryId()).isNotNull();
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1056.00");
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
  }

  @Test
  void producerAdaptersExposeOnlyOwnedMonetaryEvidence() {
    var predictionEvidence = new PredictionCorrectionEvidenceAdapter(jdbc).find(predictionId);
    assertThat(predictionEvidence).isPresent();
    assertThat(predictionEvidence.orElseThrow().identityId()).isEqualTo(bettorId);
    assertThat(predictionEvidence.orElseThrow().amount()).isEqualByComparingTo("50.00");

    UUID authorization = service.authorize(eventId, administratorId, "auth-evidence");
    service.process(authorization);
    UUID settlementId = service.findLatestByPrediction(predictionId).orElseThrow().settlementId();
    var settlementEvidence = new SettlementCorrectionEvidenceAdapter(jdbc).find(settlementId);
    assertThat(settlementEvidence).isPresent();
    assertThat(settlementEvidence.orElseThrow().identityId()).isEqualTo(bettorId);
    assertThat(settlementEvidence.orElseThrow().amount()).isEqualByComparingTo("106.00");
  }

  @Test
  void rejectsMissingRoleAndStaleResultBeforeCredit() {
    administrators.clear();
    assertThatThrownBy(() -> service.authorize(eventId, administratorId, "auth-denied"))
        .isInstanceOf(SecurityException.class);
    administrators.add(administratorId);
    UUID authorization = service.authorize(eventId, administratorId, "auth-stale");
    jdbc.update("UPDATE public.sports_event SET status = 'SUSPENSO' WHERE event_id = ?", eventId);
    assertThatThrownBy(() -> service.process(authorization))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ledgerEntries("PAYOUT")).isZero();
  }

  @Test
  void revokingAdministratorAfterAuthorizationPreventsProcessing() {
    UUID authorization = service.authorize(eventId, administratorId, "auth-revoked");
    administrators.remove(administratorId);
    assertThatThrownBy(() -> service.process(authorization)).isInstanceOf(SecurityException.class);
    assertThat(ledgerEntries("PAYOUT")).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.settlement_case", Integer.class))
        .isZero();
  }

  @Test
  void authorizationKeyCannotBeReusedByAnotherAdministrator() {
    UUID authorization = service.authorize(eventId, administratorId, "auth-conflict");
    administrators.add(bettorId);
    assertThatThrownBy(() -> service.authorize(eventId, bettorId, "auth-conflict"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different payload");
    assertThat(repository.findAuthorization(authorization).orElseThrow().authorizedByIdentityId())
        .isEqualTo(administratorId);
  }

  @Test
  void correctedResultAfterPayoutRecoversCreditAndCreatesSupersedingUnit() {
    UUID originalAuthorization =
        service.authorize(eventId, administratorId, "auth-before-correction");
    service.process(originalAuthorization);
    UUID originalSettlementId =
        service.findLatestByPrediction(predictionId).orElseThrow().settlementId();
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1056.00");

    new TransactionTemplate(manager)
        .executeWithoutResult(
            ignored -> {
              UUID correctedVersionId =
                  jdbc.queryForObject(
                      "INSERT INTO public.sports_result_version (event_id, competition_id, edition_id, version_no, status, source_reference, reason, recorded_by_identity_id, confirmed_by_identity_id, confirmed_at, supersedes_version_id, base_lock_version) VALUES (?, ?, ?, 2, 'CORRIGIDO', 'https://source.invalid/corrected-result', 'score corrected', ?, ?, CURRENT_TIMESTAMP, ?, 0) RETURNING version_id",
                      UUID.class,
                      eventId,
                      competitionId,
                      editionId,
                      administratorId,
                      administratorId,
                      resultVersionId);
              jdbc.update(
                  "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 1, 1, 'Home'), (?, 2, 2, 'Away')",
                  correctedVersionId,
                  correctedVersionId);
              jdbc.update(
                  "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
                  correctedVersionId,
                  eventId);
              UUID correctedFactId =
                  jdbc.queryForObject(
                      "INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, reason, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, 2, 'SPORTING_RESULT', 'https://source.invalid/corrected-result', 'score corrected', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?) RETURNING fact_id",
                      UUID.class,
                      eventId,
                      competitionId,
                      editionId,
                      administratorId,
                      resultFactId);
              jdbc.update(
                  "INSERT INTO public.sports_outcome_sporting_payload (fact_id, event_id, confirmed_version_id, confirmed_version_status, confirmed_at) VALUES (?, ?, ?, 'CORRIGIDO', CURRENT_TIMESTAMP)",
                  correctedFactId,
                  eventId,
                  correctedVersionId);
              jdbc.update(
                  "UPDATE public.sports_outcome_head SET fact_id = ? WHERE event_id = ?",
                  correctedFactId,
                  eventId);
            });

    UUID correctedAuthorization = service.authorize(eventId, administratorId, "auth-corrected");
    UUID correctedCaseId = service.process(correctedAuthorization);
    SettlementReadPort.PredictionOutcome corrected =
        service.findLatestByPrediction(predictionId).orElseThrow();
    assertThat(corrected.settlementId()).isNotEqualTo(originalSettlementId);
    assertThat(corrected.state()).isEqualTo(SettlementState.LOST);
    assertThat(service.findCase(correctedCaseId).orElseThrow().status())
        .isEqualTo(SettlementReadPort.ProcessingStatus.COMPLETE);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Integer.class))
        .isEqualTo(2);
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
    assertThat(ledgerEntries("REFUND")).isZero();
    assertThat(ledgerEntries("CORRECTION_RECOVERY")).isEqualTo(1);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("950.00");
    assertThat(service.process(correctedAuthorization)).isEqualTo(correctedCaseId);
  }

  @Test
  void cancellationRefundsStakeWithoutInventingScore() {
    cancelEvent();
    UUID authorization = service.authorize(eventId, administratorId, "auth-cancel");
    UUID caseId = service.process(authorization);
    SettlementReadPort.PredictionOutcome outcome =
        service.findLatestByPrediction(predictionId).orElseThrow();
    assertThat(outcome.state()).isEqualTo(SettlementState.REFUNDED);
    assertThat(outcome.reason()).isEqualTo("EVENT_CANCELLED");
    assertThat(outcome.homeScore()).isNull();
    assertThat(outcome.awayScore()).isNull();
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1000.00");
    assertThat(ledgerEntries("REFUND")).isEqualTo(1);
    assertThat(service.process(authorization)).isEqualTo(caseId);
  }

  @Test
  void spentPayoutBecomesPendingAndNewRefundPaysDownTheOldDebt() {
    UUID original = service.authorize(eventId, administratorId, "auth-spent-original");
    service.process(original);
    ledger.debit(bettorId, new BigDecimal("1050.00"), UUID.randomUUID());
    cancelEvent();
    UUID corrected = service.authorize(eventId, administratorId, "auth-spent-cancel");
    UUID correctedCase = service.process(corrected);

    assertThat(service.findLatestByPrediction(predictionId).orElseThrow().state())
        .isEqualTo(SettlementState.REFUNDED);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("0.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT amount FROM public.credit_correction_case WHERE replacement_settlement_id = ?",
                BigDecimal.class,
                service.findLatestByPrediction(predictionId).orElseThrow().settlementId()))
        .isEqualByComparingTo("106.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0.00) FROM public.credit_correction_allocation",
                BigDecimal.class))
        .isEqualByComparingTo("56.00");
    ledger.payout(bettorId, new BigDecimal("100.00"), UUID.randomUUID());
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("50.00");
    assertThat(service.process(corrected)).isEqualTo(correctedCase);
    assertThat(ledgerEntries("CORRECTION_RECOVERY")).isEqualTo(3);
  }

  @Test
  void lossRecordsNoCreditEntry() {
    UUID losingPrediction = addPrediction("AWAY");
    UUID losingIdentity =
        jdbc.queryForObject(
            "SELECT identity_id FROM public.prediction WHERE prediction_id = ?",
            UUID.class,
            losingPrediction);
    UUID authorization = service.authorize(eventId, administratorId, "auth-loss");
    service.process(authorization);
    assertThat(service.findLatestByPrediction(losingPrediction).orElseThrow().state())
        .isEqualTo(SettlementState.LOST);
    assertThat(ledger.getBalance(losingIdentity).balance()).isEqualByComparingTo("950.00");
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
  }
}

abstract class SettlementFixture {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_settlement_execution")
          .withUsername("betufu_settlement_execution")
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
            return Optional.empty();
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
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, 'elo-v1', ?, 50.00, 2.1200000000, 106.00, 56.00, CURRENT_TIMESTAMP, ?, ?)",
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
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, 'elo-v1', 'HOME', 50.00, 2.1200000000, 106.00, 56.00, CURRENT_TIMESTAMP, 'prediction-main', 'digest-main')",
        predictionId,
        bettorId,
        marketId,
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  private UUID createIdentity() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        id,
        id.toString());
    return id;
  }
}
