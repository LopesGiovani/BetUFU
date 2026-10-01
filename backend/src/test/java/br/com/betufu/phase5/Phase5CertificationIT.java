package br.com.betufu.phase5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.betufu.betting.PredictionAcceptancePort;
import br.com.betufu.betting.PredictionTicket;
import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.betting.internal.PredictionService;
import br.com.betufu.identity.OperationalRole;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.odds.OddsForPredictionPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.verification.EnrollmentEligibility;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Phase5CertificationIT {
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");
  private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_phase5_certification")
          .withUsername("betufu_phase5_certification")
          .withPassword("betufu-phase5-certification-test");

  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;
  private DataSourceTransactionManager transactionManager;

  @BeforeEach
  void configureDataSource() {
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    resetSchema();
  }

  @Test
  void v32BackfillsOnlyActiveEnrollmentsAndReplaysWithoutDuplicatingCredit() {
    migrateTo("28");
    UUID active = createIdentity("phase5-active");
    UUID inactive = createIdentity("phase5-inactive");
    insertEnrollment(active, "11", "ATIVO");
    insertEnrollment(inactive, "22", "LIBERADO");

    migrateTo("32");
    assertInitialGrant(active);
    assertThat(accountCount(inactive)).isZero();

    jdbc.update("DELETE FROM public.flyway_schema_history WHERE version = '32'");
    migrateTo("32");

    assertInitialGrant(active);
    assertThat(accountCount(inactive)).isZero();
  }

  @Test
  void staleAndOverPrecisionPayloadsUseStableErrorsWithoutEffects() {
    migrateLatest();
    UUID identity = createIdentity("phase5-validation-errors");
    UUID market = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    UUID competition = UUID.randomUUID();
    CreditLedgerService ledger = ledger();
    ledger.ensureInitialGrant(identity);
    PredictionEligibilityPolicy eligibility = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(eligibility.isEligible(Mockito.any(), Mockito.any())).thenReturn(true);
    PredictionService service =
        service(
            ignored ->
                new OddsForPredictionPort.MarketSnapshot(
                    market,
                    UUID.randomUUID(),
                    competition,
                    "OPEN",
                    version,
                    publication,
                    "rule-v1",
                    NOW.plusSeconds(3600),
                    List.of(
                        new OddsForPredictionPort.Selection(
                            "HOME", "Mandante", new BigDecimal("2.00")))),
            eligibility,
            ledger);

    Throwable stale =
        catchThrowable(
            () ->
                service.accept(
                    identity,
                    new PredictionAcceptancePort.SubmitPrediction(
                        market,
                        UUID.randomUUID(),
                        publication,
                        "rule-v1",
                        "HOME",
                        new BigDecimal("10.00"),
                        "phase5-stale")));
    assertThat(stale)
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.STALE_VERSION);
            });

    Throwable overPrecision =
        catchThrowable(
            () ->
                service.accept(
                    identity,
                    new PredictionAcceptancePort.SubmitPrediction(
                        market,
                        version,
                        publication,
                        "rule-v1",
                        "HOME",
                        new BigDecimal("10.001"),
                        "phase5-over-precision")));
    assertThat(overPrecision)
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
            });
    assertThat(predictionCount()).isZero();
    assertThat(debitCount()).isZero();
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo("1000.00");
  }

  @Test
  void ineligibleIdentityUsesForbiddenWithoutEffects() {
    migrateLatest();
    UUID identity = createIdentity("phase5-ineligible");
    UUID market = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    CreditLedgerService ledger = ledger();
    ledger.ensureInitialGrant(identity);
    PredictionEligibilityPolicy eligibility = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(eligibility.isEligible(Mockito.any(), Mockito.any())).thenReturn(false);
    PredictionService service =
        service(
            ignored ->
                new OddsForPredictionPort.MarketSnapshot(
                    market,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "OPEN",
                    version,
                    publication,
                    "rule-v1",
                    NOW.plusSeconds(3600),
                    List.of(
                        new OddsForPredictionPort.Selection(
                            "HOME", "Mandante", new BigDecimal("2.00")))),
            eligibility,
            ledger);

    Throwable failure =
        catchThrowable(
            () ->
                service.accept(
                    identity,
                    new PredictionAcceptancePort.SubmitPrediction(
                        market,
                        version,
                        publication,
                        "rule-v1",
                        "HOME",
                        new BigDecimal("10.00"),
                        "phase5-ineligible")));

    assertThat(failure)
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
            });
    assertThat(predictionCount()).isZero();
    assertThat(debitCount()).isZero();
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo("1000.00");
  }

  @Test
  void certifiesAllInRoundingReplayDigestCompetitionAndLedgerTraceability() {
    migrateLatest();
    UUID identity = createIdentity("phase5-player");
    UUID market = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    UUID competition = UUID.randomUUID();
    CreditLedgerService ledger = ledger();
    ledger.ensureInitialGrant(identity);
    AtomicReference<UUID> checkedCompetition = new AtomicReference<>();
    PredictionEligibilityPolicy eligibility = eligibilityFor(identity, checkedCompetition);
    OddsForPredictionPort odds =
        ignored ->
            new OddsForPredictionPort.MarketSnapshot(
                market,
                UUID.randomUUID(),
                competition,
                "OPEN",
                version,
                publication,
                "elo-davidson-v1",
                NOW.plusSeconds(3600),
                List.of(
                    new OddsForPredictionPort.Selection(
                        "HOME", "Mandante", new BigDecimal("2.005"))));
    PredictionService service = service(odds, eligibility, ledger);
    PredictionAcceptancePort.SubmitPrediction command =
        new PredictionAcceptancePort.SubmitPrediction(
            market,
            version,
            publication,
            "elo-davidson-v1",
            "HOME",
            new BigDecimal("1000.00"),
            "phase5-all-in");

    PredictionTicket accepted = service.accept(identity, command);
    PredictionTicket replay = service.accept(identity, command);

    assertThat(replay.predictionId()).isEqualTo(accepted.predictionId());
    assertThat(accepted.potentialReturn()).isEqualByComparingTo("2005.00");
    assertThat(accepted.netProfit()).isEqualByComparingTo("1005.00");
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo("0.00");
    assertThat(debitCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT source_id FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
                UUID.class))
        .as("debit source_id must equal predictionId")
        .isEqualTo(accepted.predictionId());
    assertThat(checkedCompetition)
        .as("competition scope must reach eligibility policy")
        .hasValue(competition);

    PredictionAcceptancePort.SubmitPrediction divergent =
        new PredictionAcceptancePort.SubmitPrediction(
            market,
            version,
            publication,
            "elo-davidson-v1",
            "HOME",
            new BigDecimal("999.00"),
            "phase5-all-in");
    Throwable divergentFailure = catchThrowable(() -> service.accept(identity, divergent));
    assertThat(divergentFailure)
        .as("divergent idempotency payload must fail")
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.PREDICTION_CONFLICT);
            });
    assertThat(predictionCount()).isEqualTo(1);
    assertThat(debitCount()).isEqualTo(1);
  }

  @Test
  void databaseRejectsHistoryMutationAndNegativeBalance() {
    migrateLatest();
    UUID identity = createIdentity("phase5-immutable");
    CreditLedgerService ledger = ledger();
    ledger.ensureInitialGrant(identity);
    UUID market = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    UUID competition = UUID.randomUUID();
    PredictionEligibilityPolicy eligibility = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(eligibility.isEligible(Mockito.any(), Mockito.any())).thenReturn(true);
    PredictionTicket ticket =
        service(
                ignored ->
                    new OddsForPredictionPort.MarketSnapshot(
                        market,
                        UUID.randomUUID(),
                        competition,
                        "OPEN",
                        version,
                        publication,
                        "rule-v1",
                        NOW.plusSeconds(3600),
                        List.of(
                            new OddsForPredictionPort.Selection(
                                "HOME", "Mandante", new BigDecimal("2.00")))),
                eligibility,
                ledger)
            .accept(
                identity,
                new PredictionAcceptancePort.SubmitPrediction(
                    market,
                    version,
                    publication,
                    "rule-v1",
                    "HOME",
                    new BigDecimal("10.00"),
                    "phase5-immutable"));

    Throwable ledgerUpdateFailure =
        catchThrowable(
            () ->
                jdbc.update(
                    "UPDATE public.credit_ledger_entry SET amount = 20.00 WHERE entry_type = 'DEBIT'"));
    assertThat(ledgerUpdateFailure)
        .as("ledger UPDATE must remain blocked")
        .isInstanceOf(DataAccessException.class);
    Throwable ledgerDeleteFailure =
        catchThrowable(
            () -> jdbc.update("DELETE FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'"));
    assertThat(ledgerDeleteFailure)
        .as("ledger DELETE must remain blocked")
        .isInstanceOf(DataAccessException.class);
    Throwable selectionDeleteFailure =
        catchThrowable(
            () ->
                jdbc.update(
                    "DELETE FROM public.prediction_selection WHERE prediction_id = ?",
                    ticket.predictionId()));
    assertThat(selectionDeleteFailure)
        .as("prediction selection DELETE must remain blocked")
        .isInstanceOf(DataAccessException.class);
    Throwable negativeBalanceFailure =
        catchThrowable(
            () ->
                jdbc.update(
                    "UPDATE public.credit_account SET balance = -0.01 WHERE identity_id = ?",
                    identity));
    assertThat(negativeBalanceFailure)
        .as("negative balance must remain blocked")
        .isInstanceOf(DataAccessException.class);
    assertThat(predictionCount()).isEqualTo(1);
    assertThat(debitCount()).isEqualTo(1);
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo("990.00");
  }

  private PredictionService service(
      OddsForPredictionPort odds,
      PredictionEligibilityPolicy eligibility,
      CreditLedgerService ledger) {
    return new PredictionService(
        new PredictionRepository(jdbc),
        odds,
        eligibility,
        ledger,
        transactionManager,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private CreditLedgerService ledger() {
    return new CreditLedgerService(new CreditLedgerRepository(jdbc, transactionManager));
  }

  private PredictionEligibilityPolicy eligibilityFor(
      UUID eligibleIdentity, AtomicReference<UUID> checkedCompetition) {
    EnrollmentEligibility enrollment =
        new EnrollmentEligibility() {
          @Override
          public boolean hasActiveEnrollment(UUID identityId) {
            return eligibleIdentity.equals(identityId);
          }

          @Override
          public Status getStatus(UUID identityId) {
            return hasActiveEnrollment(identityId) ? Status.ATIVO : Status.SEM_VINCULO;
          }
        };
    OperationalRoleQuery roles =
        new OperationalRoleQuery() {
          @Override
          public Set<GrantedRole> findActiveRoles(UUID identityId) {
            return Set.of();
          }

          @Override
          public boolean hasRole(UUID identityId, OperationalRole role, UUID scopeRef) {
            checkedCompetition.set(scopeRef);
            return scopeRef == null;
          }
        };
    return new PredictionEligibilityPolicy(enrollment, roles);
  }

  private void resetSchema() {
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRES.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRES.getUsername()
            + "'");
  }

  private void migrateTo(String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private void migrateLatest() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }

  private UUID createIdentity(String subject) {
    return jdbc.queryForObject(
        "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', ?) RETURNING identity_id",
        UUID.class,
        subject);
  }

  private void insertEnrollment(UUID identityId, String hexByte, String status) {
    jdbc.update(
        "INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status) VALUES (?, decode(repeat(?, 32), 'hex'), 1, ?)",
        identityId,
        hexByte,
        status);
  }

  private void assertInitialGrant(UUID identityId) {
    assertThat(accountCount(identityId))
        .as("active enrollment must receive initial grant")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT balance FROM public.credit_account WHERE identity_id = ?",
                BigDecimal.class,
                identityId))
        .isEqualByComparingTo("1000.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_ledger_entry WHERE identity_id = ? AND entry_type = 'GRANT_INITIAL' AND amount = 1000.00 AND source_id = ? AND idempotency_key = ?",
                Integer.class,
                identityId,
                identityId,
                "credit:initial-grant:" + identityId))
        .isEqualTo(1);
  }

  private int accountCount(UUID identityId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.credit_account WHERE identity_id = ?",
        Integer.class,
        identityId);
  }

  private int predictionCount() {
    return jdbc.queryForObject("SELECT count(*) FROM public.prediction", Integer.class);
  }

  private int debitCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
        Integer.class);
  }
}
