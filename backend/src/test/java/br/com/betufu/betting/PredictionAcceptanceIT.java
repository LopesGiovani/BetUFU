package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.betting.internal.PredictionService;
import br.com.betufu.ledger.CreditBalance;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PredictionAcceptanceIT {
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");
  private static final UUID IDENTITY =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID MARKET =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID VERSION =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PUBLICATION =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID COMPETITION =
      UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_prediction_test")
          .withUsername("betufu_prediction")
          .withPassword("betufu-prediction-test");

  private JdbcTemplate jdbc;
  private DataSourceTransactionManager transactionManager;
  private CreditLedgerService ledger;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRES.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRES.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        IDENTITY,
        IDENTITY.toString());
    ledger = new CreditLedgerService(new CreditLedgerRepository(jdbc, transactionManager));
    ledger.ensureInitialGrant(IDENTITY);
  }

  @Test
  void usesCanonicalDigestAndReplaysBeforeRevalidatingClosedMarket() {
    AtomicReference<OddsForPredictionPort.MarketSnapshot> snapshot =
        new AtomicReference<>(snapshot("OPEN", NOW.plusSeconds(3600)));
    PredictionEligibilityPolicy policy = eligiblePolicy();
    PredictionService service = service(id -> snapshot.get(), policy, ledger);
    PredictionAcceptancePort.SubmitPrediction command = command("10.00", "prediction-key");

    PredictionTicket first = service.accept(IDENTITY, command);
    snapshot.set(snapshot("CLOSED", NOW.minusSeconds(1)));
    PredictionTicket replay = service.accept(IDENTITY, command);

    assertThat(replay.predictionId()).isEqualTo(first.predictionId());
    assertThat(
            jdbc.queryForObject(
                "SELECT payload_digest FROM public.prediction WHERE prediction_id = ?",
                String.class,
                first.predictionId()))
        .isEqualTo("de2b2707271ce3a51712778764e469e565f29a80b4c72580334651e125b8b046");
    assertThat(
            jdbc.queryForObject(
                "SELECT source_id FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
                UUID.class))
        .isEqualTo(first.predictionId());
    assertThat(ledger.getBalance(IDENTITY).balance()).isEqualByComparingTo("990.00");
    Mockito.verify(policy).isEligible(IDENTITY, COMPETITION);
  }

  @Test
  void rejectsDivergentReplayBeforeCallingOddsAgain() {
    AtomicBoolean firstLookup = new AtomicBoolean(true);
    OddsForPredictionPort odds =
        id -> {
          if (!firstLookup.getAndSet(false)) {
            throw new AssertionError("divergent replay must stop before odds lookup");
          }
          return snapshot("OPEN", NOW.plusSeconds(3600));
        };
    PredictionService service = service(odds, eligiblePolicy(), ledger);
    service.accept(IDENTITY, command("10.00", "same-key"));

    assertThatThrownBy(() -> service.accept(IDENTITY, command("11.00", "same-key")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("idempotency key conflict");
    assertThat(count("prediction")).isEqualTo(1);
    assertThat(debitCount()).isEqualTo(1);
    assertThat(ledger.getBalance(IDENTITY).balance()).isEqualByComparingTo("990.00");
  }

  @Test
  void rejectsInvalidMarketAuthorityAndPayloadWithoutEffects() {
    assertRejected(snapshot("CLOSED", NOW.plusSeconds(3600)), command("10.00", "closed"));
    assertRejected(snapshot("OPEN", NOW), command("10.00", "deadline"));
    assertRejected(
        snapshot("OPEN", NOW.plusSeconds(3600)),
        new PredictionAcceptancePort.SubmitPrediction(
            MARKET,
            UUID.randomUUID(),
            PUBLICATION,
            "elo-davidson-v1",
            "HOME",
            new BigDecimal("10.00"),
            "version"));
    assertRejected(
        snapshot("OPEN", NOW.plusSeconds(3600)),
        new PredictionAcceptancePort.SubmitPrediction(
            MARKET,
            VERSION,
            UUID.randomUUID(),
            "elo-davidson-v1",
            "HOME",
            new BigDecimal("10.00"),
            "publication"));
    assertRejected(
        snapshot("OPEN", NOW.plusSeconds(3600)),
        new PredictionAcceptancePort.SubmitPrediction(
            MARKET,
            VERSION,
            PUBLICATION,
            "other-rule",
            "HOME",
            new BigDecimal("10.00"),
            "rule"));
    assertRejected(
        snapshot("OPEN", NOW.plusSeconds(3600)),
        new PredictionAcceptancePort.SubmitPrediction(
            MARKET,
            VERSION,
            PUBLICATION,
            "elo-davidson-v1",
            "DRAW",
            new BigDecimal("10.00"),
            "selection"));
    assertRejected(snapshot("OPEN", NOW.plusSeconds(3600)), command("9.99", "stake"));
    OddsForPredictionPort.MarketSnapshot missingCompetition =
        new OddsForPredictionPort.MarketSnapshot(
            MARKET,
            UUID.randomUUID(),
            null,
            "OPEN",
            VERSION,
            PUBLICATION,
            "elo-davidson-v1",
            NOW.plusSeconds(3600),
            selections());
    assertRejected(missingCompetition, command("10.00", "competition"));

    assertThat(count("prediction")).isZero();
    assertThat(debitCount()).isZero();
    assertThat(ledger.getBalance(IDENTITY).balance()).isEqualByComparingTo("1000.00");
  }

  @Test
  void rollsBackPredictionWhenDebitFailsAfterInsert() {
    CreditLedgerPort failingLedger = Mockito.mock(CreditLedgerPort.class);
    Mockito.when(failingLedger.getBalance(IDENTITY))
        .thenReturn(new CreditBalance(IDENTITY, new BigDecimal("1000.00")));
    Mockito.when(failingLedger.debit(eq(IDENTITY), any(BigDecimal.class), any(UUID.class)))
        .thenThrow(new IllegalStateException("simulated ledger failure"));
    PredictionService service =
        service(
            id -> snapshot("OPEN", NOW.plusSeconds(3600)),
            eligiblePolicy(),
            failingLedger);

    assertThatThrownBy(() -> service.accept(IDENTITY, command("10.00", "rollback")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("simulated ledger failure");
    assertThat(count("prediction")).isZero();
    assertThat(count("prediction_selection")).isZero();
  }

  private void assertRejected(
      OddsForPredictionPort.MarketSnapshot snapshot,
      PredictionAcceptancePort.SubmitPrediction command) {
    PredictionService service = service(id -> snapshot, eligiblePolicy(), ledger);
    assertThatThrownBy(() -> service.accept(IDENTITY, command)).isInstanceOf(RuntimeException.class);
  }

  private PredictionService service(
      OddsForPredictionPort odds,
      PredictionEligibilityPolicy policy,
      CreditLedgerPort creditLedger) {
    return new PredictionService(
        new PredictionRepository(jdbc),
        odds,
        policy,
        creditLedger,
        transactionManager,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private PredictionEligibilityPolicy eligiblePolicy() {
    PredictionEligibilityPolicy policy = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(policy.isEligible(any(), any())).thenReturn(true);
    return policy;
  }

  private static PredictionAcceptancePort.SubmitPrediction command(String stake, String key) {
    return new PredictionAcceptancePort.SubmitPrediction(
        MARKET,
        VERSION,
        PUBLICATION,
        "elo-davidson-v1",
        "HOME",
        new BigDecimal(stake),
        key);
  }

  private static OddsForPredictionPort.MarketSnapshot snapshot(String state, Instant deadline) {
    return new OddsForPredictionPort.MarketSnapshot(
        MARKET,
        UUID.randomUUID(),
        COMPETITION,
        state,
        VERSION,
        PUBLICATION,
        "elo-davidson-v1",
        deadline,
        selections());
  }

  private static List<OddsForPredictionPort.Selection> selections() {
    return List.of(
        new OddsForPredictionPort.Selection("HOME", "Mandante", new BigDecimal("2.10")),
        new OddsForPredictionPort.Selection("AWAY", "Visitante", new BigDecimal("1.80")));
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
  }

  private int debitCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
        Integer.class);
  }
}
