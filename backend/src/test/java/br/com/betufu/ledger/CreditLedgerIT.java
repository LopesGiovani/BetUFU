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
class CreditLedgerIT {

  private static final DockerImageName IMAGE = DockerImageName.parse(
      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
      .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE)
      .withDatabaseName("betufu_credit_ledger_test")
      .withUsername("betufu_credit_ledger")
      .withPassword("betufu-credit-ledger-test");

  private JdbcTemplate jdbc;
  private CreditLedgerService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute("ALTER DATABASE " + POSTGRES.getDatabaseName() + " SET betufu.runtime_role TO '" + POSTGRES.getUsername() + "'");
    Flyway.configure().dataSource(dataSource).load().migrate();
    CreditLedgerRepository repository = new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(dataSource));
    service = new CreditLedgerService(repository);
  }

  @Test
  void grantsExactlyOnceAndPersistsBalance() {
    UUID identity = createIdentity();
    assertThat(service.ensureInitialGrant(identity).balance()).isEqualByComparingTo("1000.00");
    assertThat(service.ensureInitialGrant(identity).balance()).isEqualByComparingTo("1000.00");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry WHERE identity_id = ?", Long.class, identity)).isEqualTo(1L);
    assertThat(service.debit(identity, new BigDecimal("10.00"), UUID.randomUUID()).balance().balance()).isEqualByComparingTo("990.00");
    assertThat(service.getBalance(identity).balance()).isEqualByComparingTo("990.00");
  }

  @Test
  void rejectsOverdrawAndScaleAndProtectsHistory() {
    UUID identity = createIdentity();
    service.ensureInitialGrant(identity);
    assertThatThrownBy(() -> service.debit(identity, new BigDecimal("1000.01"), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.debit(identity, new BigDecimal("1.001"), UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.credit_ledger_entry"))
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbc.update("UPDATE public.credit_ledger_entry SET amount = 1.00"))
        .hasMessageContaining("immutable");
  }

  private UUID createIdentity() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)", id, id.toString());
    return id;
  }
}
