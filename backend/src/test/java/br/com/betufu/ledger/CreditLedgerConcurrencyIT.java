package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CreditLedgerConcurrencyIT {

  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_credit_ledger_concurrency_test")
          .withUsername("betufu_credit_concurrency")
          .withPassword("betufu-credit-concurrency-test");

  private JdbcTemplate jdbc;
  private CreditLedgerService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute(
        "ALTER DATABASE "
            + POSTGRES.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + POSTGRES.getUsername()
            + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    service =
        new CreditLedgerService(
            new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(dataSource)));
  }

  @Test
  void concurrentDuplicateGrantsConvergeToOneEntry() throws Exception {
    UUID identity = createIdentity();
    int workers = 8;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    try {
      List<Future<CreditBalance>> futures = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return service.ensureInitialGrant(identity);
                }));
      }
      ready.await();
      start.countDown();
      for (Future<CreditBalance> future : futures) {
        assertThat(future.get().balance()).isEqualByComparingTo("1000.00");
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_ledger_entry WHERE identity_id = ?",
                Long.class,
                identity))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT balance FROM public.credit_account WHERE identity_id = ?",
                BigDecimal.class,
                identity))
        .isEqualByComparingTo("1000.00");
  }

  @Test
  void concurrentDebitsNeverCrossZero() throws Exception {
    UUID identity = createIdentity();
    service.ensureInitialGrant(identity);
    int workers = 12;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        UUID source = UUID.randomUUID();
        futures.add(
            executor.submit(
                () -> {
                  try {
                    service.debit(identity, new BigDecimal("100.00"), source);
                  } catch (InsufficientCreditException expected) {
                    // Overdraw is a normal, safe outcome for requests beyond the balance.
                  }
                }));
      }
      for (Future<?> future : futures) future.get();
    } finally {
      executor.shutdownNow();
    }
    BigDecimal balance =
        jdbc.queryForObject(
            "SELECT balance FROM public.credit_account WHERE identity_id = ?",
            BigDecimal.class,
            identity);
    assertThat(balance).isGreaterThanOrEqualTo(BigDecimal.ZERO).isEqualByComparingTo("0.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_ledger_entry WHERE identity_id = ? AND entry_type = 'DEBIT'",
                Long.class,
                identity))
        .isEqualTo(10L);
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
