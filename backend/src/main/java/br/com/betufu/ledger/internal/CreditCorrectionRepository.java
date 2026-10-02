package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class CreditCorrectionRepository {
  private final JdbcTemplate jdbc;

  public CreditCorrectionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Case insertCase(Case value) {
    jdbc.update(
        """
            INSERT INTO public.credit_correction_case
              (case_id, identity_id, kind, origin, reason, amount,
               evidence_type, evidence_id, enrollment_id, original_entry_id,
               previous_settlement_id, replacement_settlement_id, supersedes_case_id,
               proposed_by_identity_id, description, calculation, reference_code,
               idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
        value.caseId(),
        value.identityId(),
        value.kind().name(),
        value.origin(),
        value.reason().name(),
        value.amount(),
        value.evidenceType() == null ? null : value.evidenceType().name(),
        value.evidenceId(),
        value.contextReferenceId(),
        value.originalEntryId(),
        value.previousSettlementId(),
        value.replacementSettlementId(),
        value.supersedesCaseId(),
        value.proposedById(),
        value.description(),
        value.calculation(),
        value.referenceCode(),
        value.idempotencyKey(),
        Timestamp.from(value.createdAt()));
    return findByKey(value.idempotencyKey()).orElseThrow();
  }

  public Optional<Case> findByKey(String key) {
    return jdbc.query(
        "SELECT * FROM public.credit_correction_case WHERE idempotency_key = ?",
        rs -> rs.next() ? Optional.of(mapCase(rs)) : Optional.empty(),
        key);
  }

  public Optional<Case> findCase(UUID caseId) {
    return jdbc.query(
        "SELECT * FROM public.credit_correction_case WHERE case_id = ?",
        rs -> rs.next() ? Optional.of(mapCase(rs)) : Optional.empty(),
        caseId);
  }

  public List<Case> listForOwner(UUID identityId) {
    return jdbc.query(
        "SELECT * FROM public.credit_correction_case WHERE identity_id = ? ORDER BY created_at, case_id",
        (rs, row) -> mapCase(rs),
        identityId);
  }

  public Decision insertDecision(Decision value) {
    jdbc.update(
        """
            INSERT INTO public.credit_correction_decision
              (decision_id, case_id, checker_identity_id, decision, decided_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (case_id) DO NOTHING
            """,
        value.decisionId(),
        value.caseId(),
        value.checkerId(),
        value.decision(),
        Timestamp.from(value.decidedAt()));
    return findDecision(value.caseId()).orElseThrow();
  }

  public Optional<Decision> findDecision(UUID caseId) {
    return jdbc.query(
        "SELECT * FROM public.credit_correction_decision WHERE case_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new Decision(
                        rs.getObject("decision_id", UUID.class),
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("checker_identity_id", UUID.class),
                        rs.getString("decision"),
                        rs.getTimestamp("decided_at").toInstant()))
                : Optional.empty(),
        caseId);
  }

  public List<Pending> pending(UUID identityId) {
    return jdbc.query(
        """
            SELECT c.case_id, c.amount - coalesce(sum(a.amount), 0.00) AS remaining
            FROM public.credit_correction_case c
            LEFT JOIN public.credit_correction_decision d ON d.case_id = c.case_id
            LEFT JOIN public.credit_correction_allocation a ON a.case_id = c.case_id
            WHERE c.identity_id = ? AND c.kind = 'RECOVERY'
              AND (c.origin = 'SETTLEMENT' OR d.decision = 'APPROVED')
            GROUP BY c.case_id, c.amount, c.created_at
            HAVING c.amount > coalesce(sum(a.amount), 0.00)
            ORDER BY c.created_at, c.case_id
            """,
        (rs, row) ->
            new Pending(rs.getObject("case_id", UUID.class), rs.getBigDecimal("remaining")),
        identityId);
  }

  public boolean hasPending(UUID identityId) {
    return !pending(identityId).isEmpty();
  }

  public BigDecimal recovered(UUID caseId) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(amount), 0.00) FROM public.credit_correction_allocation WHERE case_id = ?",
        BigDecimal.class,
        caseId);
  }

  public void insertAllocation(
      UUID allocationId,
      UUID caseId,
      UUID sourceEntryId,
      String sourceKey,
      UUID recoveryEntryId,
      BigDecimal amount) {
    jdbc.update(
        """
            INSERT INTO public.credit_correction_allocation
              (allocation_id, case_id, source_entry_id, source_key, recovery_entry_id, amount)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        allocationId,
        caseId,
        sourceEntryId,
        sourceKey,
        recoveryEntryId,
        amount);
  }

  public boolean ownsEntry(UUID identityId, UUID entryId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.credit_ledger_entry WHERE entry_id = ? AND identity_id = ?",
            Integer.class,
            entryId,
            identityId);
    return count != null && count == 1;
  }

  public Optional<UUID> predictionDebit(UUID identityId, UUID predictionId, BigDecimal amount) {
    return jdbc.query(
        """
            SELECT entry_id FROM public.credit_ledger_entry
            WHERE identity_id = ? AND entry_type = 'DEBIT' AND source_type = 'PREDICTION'
              AND source_id = ? AND amount = ?
            """,
        rs -> rs.next() ? Optional.of(rs.getObject("entry_id", UUID.class)) : Optional.empty(),
        identityId,
        predictionId,
        amount);
  }

  public Optional<SettlementCredit> settlementCredit(UUID identityId, UUID settlementId) {
    return jdbc.query(
        """
            SELECT entry_id, amount FROM public.credit_ledger_entry
            WHERE identity_id = ? AND source_type = 'SETTLEMENT' AND source_id = ?
              AND entry_type IN ('PAYOUT', 'REFUND')
            """,
        rs ->
            rs.next()
                ? Optional.of(
                    new SettlementCredit(
                        rs.getObject("entry_id", UUID.class), rs.getBigDecimal("amount")))
                : Optional.empty(),
        identityId,
        settlementId);
  }

  public boolean replacementMatches(
      UUID identityId, UUID previousSettlementId, UUID replacementSettlementId) {
    Integer count =
        jdbc.queryForObject(
            """
                SELECT count(*) FROM public.prediction_settlement
                WHERE settlement_id = ? AND identity_id = ? AND supersedes_settlement_id = ?
                """,
            Integer.class,
            replacementSettlementId,
            identityId,
            previousSettlementId);
    return count != null && count == 1;
  }

  private static Case mapCase(ResultSet rs) throws SQLException {
    return new Case(
        rs.getObject("case_id", UUID.class),
        rs.getObject("identity_id", UUID.class),
        Kind.valueOf(rs.getString("kind")),
        rs.getString("origin"),
        Reason.valueOf(rs.getString("reason")),
        rs.getBigDecimal("amount"),
        rs.getString("evidence_type") == null ? null : Type.valueOf(rs.getString("evidence_type")),
        rs.getObject("evidence_id", UUID.class),
        rs.getObject("enrollment_id", UUID.class),
        rs.getObject("original_entry_id", UUID.class),
        rs.getObject("previous_settlement_id", UUID.class),
        rs.getObject("replacement_settlement_id", UUID.class),
        rs.getObject("supersedes_case_id", UUID.class),
        rs.getObject("proposed_by_identity_id", UUID.class),
        rs.getString("description"),
        rs.getString("calculation"),
        rs.getString("reference_code"),
        rs.getString("idempotency_key"),
        rs.getTimestamp("created_at").toInstant());
  }

  public record Case(
      UUID caseId,
      UUID identityId,
      Kind kind,
      String origin,
      Reason reason,
      BigDecimal amount,
      Type evidenceType,
      UUID evidenceId,
      UUID contextReferenceId,
      UUID originalEntryId,
      UUID previousSettlementId,
      UUID replacementSettlementId,
      UUID supersedesCaseId,
      UUID proposedById,
      String description,
      String calculation,
      String referenceCode,
      String idempotencyKey,
      Instant createdAt) {}

  public record Decision(
      UUID decisionId, UUID caseId, UUID checkerId, String decision, Instant decidedAt) {}

  public record Pending(UUID caseId, BigDecimal remaining) {}

  public record SettlementCredit(UUID entryId, BigDecimal amount) {}
}
