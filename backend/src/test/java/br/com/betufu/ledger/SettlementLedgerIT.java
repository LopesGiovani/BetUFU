package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import java.math.BigDecimal;
import java.util.UUID;
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
class SettlementLedgerIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_settlement_ledger")
          .withUsername("betufu_settlement_ledger")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  private JdbcTemplate jdbc;
  private CreditLedgerService ledger;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).load().migrate();
    jdbc.execute("TRUNCATE TABLE public.local_identity CASCADE");
    ledger =
        new CreditLedgerService(
            new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(source)));
  }

  @Test
  void payoutAndRefundHaveDistinctReplaySafeCreditFacts() {
    UUID identity = identity();
    UUID payoutUnit = UUID.randomUUID();
    UUID refundUnit = UUID.randomUUID();
    assertThat(ledger.payout(identity, new BigDecimal("106.005"), payoutUnit).balance().balance())
        .isEqualByComparingTo("106.01");
    assertThat(ledger.payout(identity, new BigDecimal("106.005"), payoutUnit).replayed()).isTrue();
    assertThat(ledger.refund(identity, new BigDecimal("50.00"), refundUnit).balance().balance())
        .isEqualByComparingTo("156.01");
    assertThat(ledger.refund(identity, new BigDecimal("50.00"), refundUnit).replayed()).isTrue();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT entry_type FROM public.credit_ledger_entry ORDER BY entry_type",
                String.class))
        .containsExactly("PAYOUT", "REFUND");
  }

  @Test
  void conflictingReplayFailsWithoutChangingBalance() {
    UUID identity = identity();
    UUID other = identity();
    UUID unit = UUID.randomUUID();
    ledger.payout(identity, new BigDecimal("10.00"), unit);
    assertThatThrownBy(() -> ledger.payout(identity, new BigDecimal("11.00"), unit))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> ledger.payout(other, new BigDecimal("10.00"), unit))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ledger.getBalance(identity).balance()).isEqualByComparingTo("10.00");
    assertThat(ledger.getBalance(other).balance()).isEqualByComparingTo("0.00");
  }

  private UUID identity() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        id,
        id.toString());
    return id;
  }
}
