package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.betting.internal.PredictionService;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
  private static final DockerImageName IMAGE = DockerImageName.parse(
      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636").asCompatibleSubstituteFor("postgres");
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE).withDatabaseName("betufu_prediction_test").withUsername("betufu_prediction").withPassword("betufu-prediction-test");
  private JdbcTemplate jdbc;
  private PredictionService service;
  private UUID identity;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbc.execute("ALTER DATABASE " + POSTGRES.getDatabaseName() + " SET betufu.runtime_role TO '" + POSTGRES.getUsername() + "'");
    Flyway.configure().dataSource(ds).load().migrate();
    identity = UUID.randomUUID();
    jdbc.update("INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)", identity, identity.toString());
    CreditLedgerService ledger = new CreditLedgerService(new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(ds)));
    ledger.ensureInitialGrant(identity);
    OddsForPredictionPort odds = id -> new OddsForPredictionPort.MarketSnapshot(id, UUID.randomUUID(), null, "OPEN", UUID.randomUUID(), UUID.randomUUID(), "rule-v1", Instant.now().plusSeconds(3600), List.of(new OddsForPredictionPort.Selection("HOME", "Mandante", new BigDecimal("2.10"))));
    PredictionEligibilityPolicy policy = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(policy.isEligible(Mockito.any(), Mockito.any())).thenReturn(true);
    service = new PredictionService(new PredictionRepository(jdbc), odds, policy, ledger, new DataSourceTransactionManager(ds));
  }

  @Test
  void acceptsAndReplaysSameCommand() {
    UUID market = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    PredictionAcceptancePort.SubmitPrediction command = new PredictionAcceptancePort.SubmitPrediction(market, version, publication, "rule-v1", "HOME", new BigDecimal("100.00"), "prediction-key");
    // Adapter snapshot is generated per call, so use a stable fake for exact version matching.
    OddsForPredictionPort stable = id -> new OddsForPredictionPort.MarketSnapshot(id, UUID.randomUUID(), null, "OPEN", version, publication, "rule-v1", Instant.now().plusSeconds(3600), List.of(new OddsForPredictionPort.Selection("HOME", "Mandante", new BigDecimal("2.10"))));
    service = new PredictionService(new PredictionRepository(jdbc), stable, Mockito.mock(PredictionEligibilityPolicy.class), new CreditLedgerService(new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()))), new DataSourceTransactionManager(jdbc.getDataSource()));
    PredictionEligibilityPolicy policy = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(policy.isEligible(Mockito.any(), Mockito.any())).thenReturn(true);
    service = new PredictionService(new PredictionRepository(jdbc), stable, policy, new CreditLedgerService(new CreditLedgerRepository(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()))), new DataSourceTransactionManager(jdbc.getDataSource()));
    PredictionTicket first = service.accept(identity, command);
    PredictionTicket replay = service.accept(identity, command);
    assertThat(replay.predictionId()).isEqualTo(first.predictionId());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Long.class)).isEqualTo(1L);
  }
}
