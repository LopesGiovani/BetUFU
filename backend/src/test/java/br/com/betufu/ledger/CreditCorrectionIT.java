package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.betting.internal.PredictionCorrectionEvidenceAdapter;
import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.Status;
import br.com.betufu.ledger.CreditCorrectionModels.View;
import br.com.betufu.ledger.internal.CreditAllocationService;
import br.com.betufu.ledger.internal.CreditCorrectionRepository;
import br.com.betufu.ledger.internal.CreditCorrectionService;
import br.com.betufu.ledger.internal.CreditLedgerRepository;
import br.com.betufu.ledger.internal.CreditLedgerService;
import br.com.betufu.ledger.internal.LedgerCorrectionEvidenceAdapter;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import br.com.betufu.settlement.internal.SettlementCorrectionEvidenceAdapter;
import br.com.betufu.verification.internal.EnrollmentCorrectionEvidenceAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
class CreditCorrectionIT extends CreditCorrectionFixture {
  @Test
  void recoversAvailableBalanceThenFutureCreditsWithoutNegativeBalance() {
    ledger.ensureInitialGrant(owner);
    ledger.debit(owner, new BigDecimal("950.00"), UUID.randomUUID());
    View proposed = propose(Kind.RECOVERY, "100.00", "recover-spent");
    View approved = corrections.approve(checker, proposed.caseId());
    assertThat(approved.status()).isEqualTo(Status.PENDING);
    assertThat(approved.recovered()).isEqualByComparingTo("50.00");
    assertThat(approved.pending()).isEqualByComparingTo("50.00");
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");

    ledger.payout(owner, new BigDecimal("30.00"), UUID.randomUUID());
    assertThat(corrections.findForOwner(owner, proposed.caseId()).pending())
        .isEqualByComparingTo("20.00");
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");
    ledger.refund(owner, new BigDecimal("40.00"), UUID.randomUUID());
    View finished = corrections.findForOwner(owner, proposed.caseId());
    assertThat(finished.recovered()).isEqualByComparingTo("100.00");
    assertThat(finished.pending()).isEqualByComparingTo("0.00");
    assertThat(finished.status()).isEqualTo(Status.RECOVERED);
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("20.00");
    assertThat(recoveryEntries()).isEqualTo(3);
    assertThat(
            auditEntries.stream()
                .filter(
                    entry -> AuditEntry.ACTION_CREDIT_CORRECTION_RECOVERED.equals(entry.action())))
        .hasSize(3);
  }

  @Test
  void pendingCasesConsumeCreditsInStableFifoOrder() {
    ledger.ensureInitialGrant(owner);
    ledger.debit(owner, new BigDecimal("1000.00"), UUID.randomUUID());
    View firstProposal = propose(Kind.RECOVERY, "30.00", "first");
    View secondProposal = propose(Kind.RECOVERY, "40.00", "second");
    View first = corrections.approve(checker, firstProposal.caseId());
    View second = corrections.approve(checker, secondProposal.caseId());
    ledger.payout(owner, new BigDecimal("50.00"), UUID.randomUUID());
    assertThat(corrections.findForOwner(owner, first.caseId()).status())
        .isEqualTo(Status.RECOVERED);
    assertThat(corrections.findForOwner(owner, second.caseId()).recovered())
        .isEqualByComparingTo("20.00");
    assertThat(corrections.findForOwner(owner, second.caseId()).pending())
        .isEqualByComparingTo("20.00");
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");
  }

  @Test
  void proposalAndApprovalRetriesAreIdempotentAndConflictsFail() {
    View first = propose(Kind.CREDIT, "25.00", "same-key");
    View repeated = propose(Kind.CREDIT, "25.00", "same-key");
    assertThat(repeated.caseId()).isEqualTo(first.caseId());
    assertThatThrownBy(() -> propose(Kind.CREDIT, "26.00", "same-key"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                corrections.propose(
                    maker,
                    owner,
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    new BigDecimal("25.00"),
                    first.evidenceType(),
                    first.evidenceId(),
                    null,
                    null,
                    "different explanation",
                    "25.00 recorded against source",
                    "same-key"))
        .isInstanceOf(IllegalStateException.class);
    corrections.approve(checker, first.caseId());
    corrections.approve(checker, first.caseId());
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("25.00");
    assertThat(
            auditEntries.stream()
                .filter(
                    entry -> AuditEntry.ACTION_CREDIT_CORRECTION_APPROVED.equals(entry.action())))
        .hasSize(1);
  }

  @Test
  void correctionOfCorrectionCreatesNewFacts() {
    View credit = propose(Kind.CREDIT, "25.00", "credit-first");
    corrections.approve(checker, credit.caseId());
    View reversal =
        corrections.propose(
            maker,
            owner,
            Kind.RECOVERY,
            Reason.PROVEN_TECHNICAL_FAILURE,
            new BigDecimal("25.00"),
            credit.evidenceType(),
            credit.evidenceId(),
            null,
            credit.caseId(),
            "reverse duplicate",
            "25.00 original credit",
            "reverse-credit");
    corrections.approve(checker, reversal.caseId());
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");
    assertThat(corrections.findForOwner(owner, reversal.caseId()).status())
        .isEqualTo(Status.RECOVERED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.credit_correction_case", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void maximumLengthCommandKeyDoesNotOverflowAuditKey() {
    View proposed = propose(Kind.CREDIT, "10.00", "a".repeat(255));
    assertThat(proposed.status()).isEqualTo(Status.PROPOSED);
    assertThat(auditEntries.getFirst().idempotencyKey().length()).isLessThanOrEqualTo(255);
  }
}

abstract class CreditCorrectionFixture {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_credit_correction")
          .withUsername("betufu_credit_correction")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  protected JdbcTemplate jdbc;
  protected DataSourceTransactionManager manager;
  protected CreditLedgerService ledger;
  protected CreditCorrectionService corrections;
  protected List<AuditEntry> auditEntries;
  protected UUID maker;
  protected UUID checker;
  protected UUID owner;
  protected Set<UUID> administrators;
  protected Map<String, UUID> evidenceByKey;

  @BeforeEach
  void setUpCorrections() {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    manager = new DataSourceTransactionManager(source);
    Flyway.configure().dataSource(source).load().migrate();
    jdbc.execute("TRUNCATE TABLE public.local_identity CASCADE");
    auditEntries = new ArrayList<>();
    evidenceByKey = new HashMap<>();
    CreditLedgerRepository ledgerRepository = new CreditLedgerRepository(jdbc, manager);
    CreditCorrectionRepository correctionRepository = new CreditCorrectionRepository(jdbc);
    ledger =
        new CreditLedgerService(
            ledgerRepository,
            new CreditAllocationService(correctionRepository, ledgerRepository, auditEntries::add));
    administrators = java.util.concurrent.ConcurrentHashMap.newKeySet();
    OperationalIdentityLookup identities =
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
    AuditRecorder audit = auditEntries::add;
    corrections =
        new CreditCorrectionService(
            correctionRepository,
            ledger,
            identities,
            audit,
            List.of(
                new LedgerCorrectionEvidenceAdapter(jdbc),
                new PredictionCorrectionEvidenceAdapter(jdbc),
                new SettlementCorrectionEvidenceAdapter(jdbc)),
            new EnrollmentCorrectionEvidenceAdapter(jdbc),
            manager);
    maker = identity();
    checker = identity();
    owner = identity();
    administrators.add(maker);
    administrators.add(checker);
  }

  protected UUID identity() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, 'https://idp.invalid', ?)",
        id,
        id.toString());
    return id;
  }

  protected View propose(Kind kind, String amount, String key) {
    UUID evidenceId = evidenceByKey.computeIfAbsent(key, ignored -> evidence(kind, amount));
    return corrections.propose(
        maker,
        owner,
        kind,
        Reason.PROVEN_TECHNICAL_FAILURE,
        new BigDecimal(amount),
        Type.LEDGER_ENTRY,
        evidenceId,
        null,
        null,
        "verified technical error",
        amount + " recorded against source",
        key);
  }

  protected UUID evidence(Kind kind, String amount) {
    var paid = ledger.payout(owner, new BigDecimal(amount), UUID.randomUUID());
    var spent = ledger.debit(owner, new BigDecimal(amount), UUID.randomUUID());
    return kind == Kind.CREDIT ? spent.entryId() : paid.entryId();
  }

  protected int recoveryEntries() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_type = 'CORRECTION_RECOVERY'",
        Integer.class);
  }
}
