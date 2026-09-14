package br.com.betufu.betting;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.betting.internal.PredictionService;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
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
class PredictionConcurrencyIT {
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");
  private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_prediction_concurrency")
          .withUsername("betufu_prediction_concurrency")
          .withPassword("betufu-prediction-concurrency-test");

  private JdbcTemplate jdbc;
  private PredictionService service;
  private CreditLedgerService ledger;
  private UUID identity;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRES.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRES.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    identity = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        identity,
        identity.toString());
    ledger = new CreditLedgerService(new CreditLedgerRepository(jdbc, transactionManager));
    ledger.ensureInitialGrant(identity);
    UUID competition = UUID.randomUUID();
    OddsForPredictionPort odds =
        marketId ->
            new OddsForPredictionPort.MarketSnapshot(
                marketId,
                UUID.randomUUID(),
                competition,
                "OPEN",
                marketId,
                marketId,
                "rule-v1",
                NOW.plusSeconds(3600),
                List.of(
                    new OddsForPredictionPort.Selection(
                        "HOME", "Mandante", new BigDecimal("2.00"))));
    PredictionEligibilityPolicy policy = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(policy.isEligible(Mockito.any(), Mockito.any())).thenReturn(true);
    service =
        new PredictionService(
            new PredictionRepository(jdbc),
            odds,
            policy,
            ledger,
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void concurrentIdenticalRetriesReturnOneTicketAndOneDebit() throws Exception {
    UUID market = UUID.randomUUID();
    PredictionAcceptancePort.SubmitPrediction command = command(market, "same-key", "100.00");

    List<Attempt> attempts =
        race(() -> service.accept(identity, command), () -> service.accept(identity, command));

    assertThat(attempts).allMatch(Attempt::succeeded);
    assertThat(attempts.get(0).ticket().predictionId())
        .isEqualTo(attempts.get(1).ticket().predictionId());
    assertCountsAndBalance(1, 1, "900.00");
  }

  @Test
  void concurrentDifferentKeysForSameMarketCreateOnlyOnePrediction() throws Exception {
    UUID market = UUID.randomUUID();

    List<Attempt> attempts =
        race(
            () -> service.accept(identity, command(market, "key-a", "100.00")),
            () -> service.accept(identity, command(market, "key-b", "100.00")));

    assertThat(attempts.stream().filter(Attempt::succeeded)).hasSize(1);
    assertThat(attempts.stream().filter(attempt -> !attempt.succeeded())).hasSize(1);
    assertCountsAndBalance(1, 1, "900.00");
  }

  @Test
  void concurrentDivergentPayloadsForSameKeyCreateOnlyOnePrediction() throws Exception {
    UUID market = UUID.randomUUID();

    List<Attempt> attempts =
        race(
            () -> service.accept(identity, command(market, "shared-key", "100.00")),
            () -> service.accept(identity, command(market, "shared-key", "200.00")));

    assertThat(attempts.stream().filter(Attempt::succeeded)).hasSize(1);
    assertThat(attempts.stream().filter(attempt -> !attempt.succeeded())).hasSize(1);
    BigDecimal expectedBalance =
        attempts.stream().filter(Attempt::succeeded).findFirst().orElseThrow().ticket().stake()
            .compareTo(new BigDecimal("100.00"))
                == 0
            ? new BigDecimal("900.00")
            : new BigDecimal("800.00");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
                Integer.class))
        .isEqualTo(1);
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo(expectedBalance);
  }

  @Test
  void concurrentMarketsSerializeTheAccountAndNeverOverdraw() throws Exception {
    UUID marketA = UUID.randomUUID();
    UUID marketB = UUID.randomUUID();

    List<Attempt> attempts =
        race(
            () -> service.accept(identity, command(marketA, "market-a", "600.00")),
            () -> service.accept(identity, command(marketB, "market-b", "600.00")));

    assertThat(attempts.stream().filter(Attempt::succeeded)).hasSize(1);
    assertThat(attempts.stream().filter(attempt -> !attempt.succeeded())).hasSize(1);
    assertCountsAndBalance(1, 1, "400.00");
  }

  private PredictionAcceptancePort.SubmitPrediction command(
      UUID market, String key, String stake) {
    return new PredictionAcceptancePort.SubmitPrediction(
        market, market, market, "rule-v1", "HOME", new BigDecimal(stake), key);
  }

  private List<Attempt> race(Supplier<PredictionTicket> first, Supplier<PredictionTicket> second)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Attempt>> futures = new ArrayList<>();
      futures.add(executor.submit(() -> attempt(first, ready, start)));
      futures.add(executor.submit(() -> attempt(second, ready, start)));
      assertThat(ready.await(10, SECONDS)).isTrue();
      start.countDown();
      List<Attempt> attempts = new ArrayList<>();
      for (Future<Attempt> future : futures) {
        attempts.add(future.get(20, SECONDS));
      }
      return attempts;
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private Attempt attempt(
      Supplier<PredictionTicket> action, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      return new Attempt(action.get(), null);
    } catch (RuntimeException ex) {
      return new Attempt(null, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return new Attempt(null, ex);
    }
  }

  private void assertCountsAndBalance(int predictions, int debits, String balance) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Integer.class))
        .isEqualTo(predictions);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = 'DEBIT'",
                Integer.class))
        .isEqualTo(debits);
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo(balance);
  }

  private record Attempt(PredictionTicket ticket, Exception failure) {
    private boolean succeeded() {
      return ticket != null && failure == null;
    }
  }
}
