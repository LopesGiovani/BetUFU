package br.com.betufu.phase5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.betting.PredictionAcceptancePort;
import br.com.betufu.betting.PredictionTicket;
import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.betting.internal.PredictionService;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditBalanceModels.CreditBalanceResponse;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.odds.OddsForPredictionPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.api.ProblemDetailsHandler;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Phase5PrivacyIT {
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");
  private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_phase5_privacy")
          .withUsername("betufu_phase5_privacy")
          .withPassword("betufu-phase5-privacy-test");

  private static final Set<String> PRIVATE_VOCABULARY =
      Set.of(
          "identityId",
          "idempotencyKey",
          "payloadDigest",
          "email",
          "matricula",
          "hmac",
          "token",
          "role",
          "scope");

  private JdbcTemplate jdbc;
  private PredictionRepository repository;
  private PredictionService service;
  private UUID firstIdentity;
  private UUID secondIdentity;
  private UUID marketId;

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
    firstIdentity = insertIdentity("first-owner");
    secondIdentity = insertIdentity("second-owner");
    CreditLedgerService ledger =
        new CreditLedgerService(new CreditLedgerRepository(jdbc, transactionManager));
    ledger.ensureInitialGrant(firstIdentity);
    ledger.ensureInitialGrant(secondIdentity);
    marketId = UUID.randomUUID();
    UUID competitionId = UUID.randomUUID();
    PredictionEligibilityPolicy eligibility = Mockito.mock(PredictionEligibilityPolicy.class);
    Mockito.when(eligibility.isEligible(Mockito.any(), Mockito.eq(competitionId))).thenReturn(true);
    repository = new PredictionRepository(jdbc);
    service =
        new PredictionService(
            repository,
            requestedMarket ->
                new OddsForPredictionPort.MarketSnapshot(
                    requestedMarket,
                    UUID.randomUUID(),
                    competitionId,
                    "OPEN",
                    requestedMarket,
                    requestedMarket,
                    "rule-v1",
                    NOW.plusSeconds(3600),
                    List.of(
                        new OddsForPredictionPort.Selection(
                            "HOME", "Mandante", new BigDecimal("2.00")),
                        new OddsForPredictionPort.Selection(
                            "AWAY", "Visitante", new BigDecimal("3.00")))),
            eligibility,
            ledger,
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void persistedTicketsAreStrictlyIsolatedByAuthenticatedIdentity() {
    PredictionTicket first =
        service.accept(firstIdentity, command("HOME", "100.00", "first-receipt"));
    PredictionTicket second =
        service.accept(secondIdentity, command("AWAY", "200.00", "second-receipt"));

    assertThat(repository.findByMarket(firstIdentity, marketId)).contains(first);
    assertThat(repository.findByMarket(secondIdentity, marketId)).contains(second);
    assertThat(repository.findByKey(firstIdentity, "second-receipt")).isEmpty();
    assertThat(repository.findByKey(secondIdentity, "first-receipt")).isEmpty();
    assertThat(first.predictionId()).isNotEqualTo(second.predictionId());
    assertThat(first.selection()).isEqualTo("HOME");
    assertThat(second.selection()).isEqualTo("AWAY");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void ownerResponsesExposeOnlyTheApprovedPublicFields() {
    assertThat(componentNames(PredictionTicket.class))
        .containsExactly(
            "predictionId",
            "marketId",
            "marketVersionId",
            "publicationId",
            "selection",
            "stake",
            "acceptedOdds",
            "potentialReturn",
            "netProfit",
            "ruleVersion",
            "closeDeadline",
            "state",
            "acceptedAt")
        .doesNotContainAnyElementsOf(PRIVATE_VOCABULARY);
    assertThat(componentNames(CreditBalanceResponse.class))
        .containsExactly("balance", "scale", "minimumStake", "rulesVersion")
        .doesNotContainAnyElementsOf(PRIVATE_VOCABULARY);
  }

  @Test
  void invalidCommandsUseGenericMessagesWithoutEchoingSensitiveInput() {
    PredictionService service =
        new PredictionService(
            Mockito.mock(PredictionRepository.class),
            Mockito.mock(OddsForPredictionPort.class),
            Mockito.mock(PredictionEligibilityPolicy.class),
            Mockito.mock(CreditLedgerPort.class),
            Mockito.mock(PlatformTransactionManager.class),
            Clock.systemUTC());
    String sensitiveRule = "matricula-123456-email-student@example.invalid-" + "x".repeat(80);
    PredictionAcceptancePort.SubmitPrediction command =
        new PredictionAcceptancePort.SubmitPrediction(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            sensitiveRule,
            "HOME",
            new BigDecimal("10.00"),
            "safe-key");

    assertThatThrownBy(() -> service.accept(UUID.randomUUID(), command))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED))
        .hasMessageNotContaining("matricula")
        .hasMessageNotContaining("123456")
        .hasMessageNotContaining("student@example.invalid");
  }

  @Test
  void unexpectedProblemDetailsNeverEchoExceptionOrPrivateValues() throws Exception {
    String sensitive = "matricula=123456 hmac=deadbeef token=secret saldo=1000";
    ProblemDetail problem =
        new ProblemDetailsHandler()
            .handleUnexpected(new IllegalStateException(sensitive))
            .getBody();

    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Ocorreu um erro inesperado.");
    assertThat(problem.toString())
        .doesNotContain("123456", "deadbeef", "secret", "1000", sensitive);
  }

  private static java.util.List<String> componentNames(Class<?> recordType) {
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  private UUID insertIdentity(String subject) {
    return jdbc.queryForObject(
        "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', ?) RETURNING identity_id",
        UUID.class,
        subject);
  }

  private PredictionAcceptancePort.SubmitPrediction command(
      String selection, String stake, String idempotencyKey) {
    return new PredictionAcceptancePort.SubmitPrediction(
        marketId, marketId, marketId, "rule-v1", selection, new BigDecimal(stake), idempotencyKey);
  }
}
