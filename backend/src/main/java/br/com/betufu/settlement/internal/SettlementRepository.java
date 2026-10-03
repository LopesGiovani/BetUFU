package br.com.betufu.settlement.internal;

import br.com.betufu.settlement.PredictionForSettlementPort.PredictionSnapshot;
import br.com.betufu.settlement.SettlementModels.Authorization;
import br.com.betufu.settlement.SettlementModels.AuthorizationDecision;
import br.com.betufu.settlement.SettlementModels.SettlementCase;
import br.com.betufu.settlement.SettlementModels.SettlementReason;
import br.com.betufu.settlement.SettlementModels.Unit;
import br.com.betufu.settlement.SettlementReadPort.CaseProgress;
import br.com.betufu.settlement.SettlementReadPort.CurrentFact;
import br.com.betufu.settlement.SettlementReadPort.PredictionOutcome;
import br.com.betufu.settlement.SettlementReadPort.ProcessingStatus;
import br.com.betufu.settlement.SettlementState;
import br.com.betufu.settlement.SportsResultForSettlementPort.ResultSnapshot;
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
public class SettlementRepository {
  private final JdbcTemplate jdbc;

  public SettlementRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertAuthorization(Authorization authorization) {
    jdbc.update(
        """
            INSERT INTO public.settlement_authorization
              (authorization_id, event_id, outcome_fact_id, confirmed_version_id,
               authorized_by_identity_id, decision, authorized_at, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        authorization.authorizationId(),
        authorization.eventId(),
        authorization.outcomeFactId(),
        authorization.confirmedVersionId(),
        authorization.authorizedByIdentityId(),
        authorization.decision().name(),
        Timestamp.from(authorization.authorizedAt()),
        authorization.idempotencyKey());
  }

  public void insertCase(SettlementCase settlementCase) {
    jdbc.update(
        """
            INSERT INTO public.settlement_case
              (case_id, authorization_id, event_id, outcome_fact_id,
               processing_version, rule_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
        settlementCase.caseId(),
        settlementCase.authorizationId(),
        settlementCase.eventId(),
        settlementCase.outcomeFactId(),
        settlementCase.processingVersion(),
        settlementCase.ruleVersion(),
        Timestamp.from(settlementCase.createdAt()));
  }

  public void insertUnit(Unit unit) {
    jdbc.update(
        """
            INSERT INTO public.prediction_settlement
              (settlement_id, case_id, prediction_id, identity_id, state, reason,
               amount, supersedes_settlement_id, idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        unit.settlementId(),
        unit.caseId(),
        unit.predictionId(),
        unit.identityId(),
        unit.state().name(),
        unit.reason().name(),
        unit.amount(),
        unit.supersedesSettlementId(),
        unit.idempotencyKey(),
        Timestamp.from(unit.createdAt()));
  }

  public Optional<Unit> findUnitByKey(String idempotencyKey) {
    return jdbc.query(
        "SELECT * FROM public.prediction_settlement WHERE idempotency_key = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new Unit(
                        rs.getObject("settlement_id", UUID.class),
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("prediction_id", UUID.class),
                        rs.getObject("identity_id", UUID.class),
                        SettlementState.valueOf(rs.getString("state")),
                        SettlementReason.valueOf(rs.getString("reason")),
                        rs.getBigDecimal("amount"),
                        rs.getObject("supersedes_settlement_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getTimestamp("created_at").toInstant()))
                : Optional.empty(),
        idempotencyKey);
  }

  public String lockEvent(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT status FROM public.sports_event WHERE event_id = ? FOR UPDATE",
        String.class,
        eventId);
  }

  public Authorization insertAuthorizationIfAbsent(Authorization authorization) {
    jdbc.update(
        """
            INSERT INTO public.settlement_authorization
              (authorization_id, event_id, outcome_fact_id, confirmed_version_id,
               authorized_by_identity_id, decision, authorized_at, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
        authorization.authorizationId(),
        authorization.eventId(),
        authorization.outcomeFactId(),
        authorization.confirmedVersionId(),
        authorization.authorizedByIdentityId(),
        authorization.decision().name(),
        Timestamp.from(authorization.authorizedAt()),
        authorization.idempotencyKey());
    return findAuthorizationByKey(authorization.idempotencyKey()).orElseThrow();
  }

  public Optional<Authorization> findAuthorization(UUID authorizationId) {
    return findAuthorization("authorization_id", authorizationId);
  }

  public Optional<Authorization> findAuthorizationByKey(String key) {
    return findAuthorization("idempotency_key", key);
  }

  private Optional<Authorization> findAuthorization(String column, Object value) {
    return jdbc.query(
        "SELECT * FROM public.settlement_authorization WHERE " + column + " = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new Authorization(
                        rs.getObject("authorization_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("outcome_fact_id", UUID.class),
                        rs.getObject("confirmed_version_id", UUID.class),
                        rs.getObject("authorized_by_identity_id", UUID.class),
                        AuthorizationDecision.valueOf(rs.getString("decision")),
                        rs.getTimestamp("authorized_at").toInstant(),
                        rs.getString("idempotency_key")))
                : Optional.empty(),
        value);
  }

  public Optional<SettlementCase> findCaseByAuthorization(UUID authorizationId) {
    return findCase("authorization_id", authorizationId);
  }

  public Optional<SettlementCase> findCaseByOutcome(UUID eventId, UUID outcomeFactId) {
    return jdbc.query(
        "SELECT * FROM public.settlement_case WHERE event_id = ? AND outcome_fact_id = ?",
        rs -> rs.next() ? Optional.of(mapCase(rs)) : Optional.empty(),
        eventId,
        outcomeFactId);
  }

  public Optional<SettlementCase> findCaseById(UUID caseId) {
    return findCase("case_id", caseId);
  }

  private Optional<SettlementCase> findCase(String column, UUID value) {
    return jdbc.query(
        "SELECT * FROM public.settlement_case WHERE " + column + " = ?",
        rs -> rs.next() ? Optional.of(mapCase(rs)) : Optional.empty(),
        value);
  }

  private static SettlementCase mapCase(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new SettlementCase(
        rs.getObject("case_id", UUID.class),
        rs.getObject("authorization_id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getObject("outcome_fact_id", UUID.class),
        rs.getInt("processing_version"),
        rs.getString("rule_version"),
        rs.getTimestamp("created_at").toInstant());
  }

  public int nextProcessingVersion(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT coalesce(max(processing_version), 0) + 1 FROM public.settlement_case WHERE event_id = ?",
        Integer.class,
        eventId);
  }

  public void insertProcessing(UUID caseId, int totalUnits) {
    jdbc.update(
        "INSERT INTO public.settlement_processing (case_id, total_units) VALUES (?, ?)",
        caseId,
        totalUnits);
  }

  public boolean claim(UUID caseId, UUID owner, Instant now, Instant expiry) {
    return jdbc.update(
            """
                UPDATE public.settlement_processing
                SET status = 'RUNNING', lease_owner = ?, lease_expires_at = ?, last_error = NULL,
                    updated_at = ?
                WHERE case_id = ? AND (status IN ('PENDING', 'FAILED')
                    OR (status = 'RUNNING' AND lease_expires_at <= ?))
                """,
            owner,
            Timestamp.from(expiry),
            Timestamp.from(now),
            caseId,
            Timestamp.from(now))
        == 1;
  }

  public boolean hasLeaseForUpdate(UUID caseId, UUID owner, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.query(
            "SELECT status, lease_owner, lease_expires_at FROM public.settlement_processing WHERE case_id = ? FOR UPDATE",
            rs ->
                rs.next()
                    && "RUNNING".equals(rs.getString("status"))
                    && owner.equals(rs.getObject("lease_owner", UUID.class))
                    && rs.getTimestamp("lease_expires_at").toInstant().isAfter(now),
            caseId));
  }

  public void advance(UUID caseId, UUID owner, Instant now, Instant expiry) {
    int rows =
        jdbc.update(
            "UPDATE public.settlement_processing SET completed_units = completed_units + 1, lease_expires_at = ?, updated_at = ? WHERE case_id = ? AND lease_owner = ? AND completed_units < total_units",
            Timestamp.from(expiry),
            Timestamp.from(now),
            caseId,
            owner);
    if (rows != 1) throw new IllegalStateException("settlement progress is inconsistent");
  }

  public void complete(UUID caseId, UUID owner, Instant now) {
    int rows =
        jdbc.update(
            "UPDATE public.settlement_processing SET status = 'COMPLETE', lease_owner = NULL, lease_expires_at = NULL, updated_at = ? WHERE case_id = ? AND lease_owner = ? AND completed_units = total_units",
            Timestamp.from(now),
            caseId,
            owner);
    if (rows != 1) throw new IllegalStateException("settlement still has incomplete units");
  }

  public void fail(UUID caseId, UUID owner, Instant now) {
    jdbc.update(
        "UPDATE public.settlement_processing SET status = 'FAILED', lease_owner = NULL, lease_expires_at = NULL, last_error = 'PROCESSING_FAILED', updated_at = ? WHERE case_id = ? AND lease_owner = ?",
        Timestamp.from(now),
        caseId,
        owner);
  }

  public Optional<CaseProgress> findProgress(UUID caseId) {
    return jdbc.query(
        "SELECT case_id, status, total_units, completed_units FROM public.settlement_processing WHERE case_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new CaseProgress(
                        rs.getObject("case_id", UUID.class),
                        ProcessingStatus.valueOf(rs.getString("status")),
                        rs.getInt("total_units"),
                        rs.getInt("completed_units")))
                : Optional.empty(),
        caseId);
  }

  public List<UUID> recoverable(Instant now) {
    return jdbc.query(
        "SELECT case_id FROM public.settlement_processing WHERE status IN ('PENDING', 'FAILED') OR (status = 'RUNNING' AND lease_expires_at <= ?) ORDER BY updated_at, case_id LIMIT 100",
        (rs, row) -> rs.getObject("case_id", UUID.class),
        Timestamp.from(now));
  }

  public Optional<Unit> findLatestUnit(UUID predictionId) {
    return jdbc.query(
        """
            SELECT u.* FROM public.prediction_settlement u
            JOIN public.settlement_case c ON c.case_id = u.case_id
            WHERE u.prediction_id = ? ORDER BY c.processing_version DESC LIMIT 1
            """,
        rs ->
            rs.next()
                ? Optional.of(
                    new Unit(
                        rs.getObject("settlement_id", UUID.class),
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("prediction_id", UUID.class),
                        rs.getObject("identity_id", UUID.class),
                        SettlementState.valueOf(rs.getString("state")),
                        SettlementReason.valueOf(rs.getString("reason")),
                        rs.getBigDecimal("amount"),
                        rs.getObject("supersedes_settlement_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getTimestamp("created_at").toInstant()))
                : Optional.empty(),
        predictionId);
  }

  public void insertCalculation(
      Unit unit, PredictionSnapshot prediction, ResultSnapshot result, UUID ledgerEntryId) {
    jdbc.update(
        """
            INSERT INTO public.settlement_calculation
              (settlement_id, selection, stake, accepted_odds, potential_return, outcome_kind,
               home_score, away_score, winner_slot_no, ledger_entry_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        unit.settlementId(),
        prediction.selection(),
        prediction.stake(),
        prediction.acceptedOdds(),
        prediction.potentialReturn(),
        result.kind().name(),
        result.homeScore(),
        result.awayScore(),
        result.winnerSlotNo(),
        ledgerEntryId);
  }

  public Optional<PredictionOutcome> findLatestOutcome(UUID predictionId) {
    return jdbc.query(
        """
            SELECT u.settlement_id, u.prediction_id, u.state, u.reason, u.amount,
                   c.rule_version, x.selection, x.stake, x.accepted_odds, x.potential_return,
                   x.outcome_kind, x.home_score, x.away_score, x.winner_slot_no, x.ledger_entry_id
            FROM public.prediction_settlement u
            JOIN public.settlement_case c ON c.case_id = u.case_id
            JOIN public.settlement_calculation x ON x.settlement_id = u.settlement_id
            JOIN public.settlement_processing p ON p.case_id = c.case_id AND p.status = 'COMPLETE'
            WHERE u.prediction_id = ? ORDER BY c.processing_version DESC LIMIT 1
            """,
        rs ->
            rs.next()
                ? Optional.of(
                    new PredictionOutcome(
                        rs.getObject("settlement_id", UUID.class),
                        rs.getObject("prediction_id", UUID.class),
                        SettlementState.valueOf(rs.getString("state")),
                        rs.getString("reason"),
                        rs.getString("rule_version"),
                        rs.getString("selection"),
                        rs.getBigDecimal("stake"),
                        rs.getBigDecimal("accepted_odds"),
                        rs.getBigDecimal("potential_return"),
                        rs.getBigDecimal("amount"),
                        rs.getString("outcome_kind"),
                        rs.getObject("home_score", Integer.class),
                        rs.getObject("away_score", Integer.class),
                        rs.getObject("winner_slot_no", Integer.class),
                        rs.getObject("ledger_entry_id", UUID.class)))
                : Optional.empty(),
        predictionId);
  }

  public record HistoryFact(PredictionOutcome outcome, boolean current, String status) {}

  public List<HistoryFact> findHistoryFacts(UUID predictionId, UUID eventId) {
    return jdbc.query(
        """
            SELECT u.*, c.rule_version, x.selection, x.stake, x.accepted_odds,
                   x.potential_return, x.outcome_kind, x.home_score, x.away_score,
                   x.winner_slot_no, x.ledger_entry_id, progress.status,
                   NOT EXISTS (SELECT 1 FROM public.settlement_case newer
                     WHERE newer.event_id = ? AND newer.processing_version > c.processing_version)
                     AND progress.status = 'COMPLETE' AS current
            FROM public.prediction_settlement u
            JOIN public.settlement_case c ON c.case_id = u.case_id
            JOIN public.settlement_calculation x ON x.settlement_id = u.settlement_id
            JOIN public.settlement_processing progress ON progress.case_id = c.case_id
            WHERE u.prediction_id = ? ORDER BY c.processing_version DESC
            """,
        (rs, row) ->
            new HistoryFact(
                new PredictionOutcome(
                    rs.getObject("settlement_id", UUID.class),
                    predictionId,
                    SettlementState.valueOf(rs.getString("state")),
                    rs.getString("reason"),
                    rs.getString("rule_version"),
                    rs.getString("selection"),
                    rs.getBigDecimal("stake"),
                    rs.getBigDecimal("accepted_odds"),
                    rs.getBigDecimal("potential_return"),
                    rs.getBigDecimal("amount"),
                    rs.getString("outcome_kind"),
                    rs.getObject("home_score", Integer.class),
                    rs.getObject("away_score", Integer.class),
                    rs.getObject("winner_slot_no", Integer.class),
                    rs.getObject("ledger_entry_id", UUID.class)),
                rs.getBoolean("current"),
                rs.getString("status")),
        eventId,
        predictionId);
  }

  public Optional<CaseProgress> findLatestProgressByEvent(UUID eventId) {
    return jdbc
        .query(
            """
            SELECT p.* FROM public.settlement_processing p
            JOIN public.settlement_case c ON c.case_id = p.case_id
            WHERE c.event_id = ? ORDER BY c.processing_version DESC LIMIT 1
            """,
            (rs, row) ->
                new CaseProgress(
                    rs.getObject("case_id", UUID.class),
                    ProcessingStatus.valueOf(rs.getString("status")),
                    rs.getInt("total_units"),
                    rs.getInt("completed_units")),
            eventId)
        .stream()
        .findFirst();
  }

  public boolean isAuthorized(UUID outcomeFactId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM public.settlement_authorization WHERE outcome_fact_id = ? AND decision = 'APPROVED')",
            Boolean.class,
            outcomeFactId));
  }

  public List<CurrentFact> findCurrentFacts() {
    return jdbc.query(
        """
            SELECT u.settlement_id, u.prediction_id, u.identity_id, c.event_id,
                   p.market_id, p.accepted_at, u.state, x.selection, x.accepted_odds
            FROM public.prediction_settlement u
            JOIN public.settlement_case c ON c.case_id = u.case_id
            JOIN public.settlement_processing progress
              ON progress.case_id = c.case_id AND progress.status = 'COMPLETE'
            JOIN public.prediction p ON p.prediction_id = u.prediction_id
            JOIN public.settlement_calculation x ON x.settlement_id = u.settlement_id
            WHERE u.state IN ('WON', 'LOST')
              AND NOT EXISTS (
                SELECT 1 FROM public.settlement_case newer
                WHERE newer.event_id = c.event_id
                  AND newer.processing_version > c.processing_version
              )
            ORDER BY p.accepted_at, u.prediction_id
            """,
        (rs, row) ->
            new CurrentFact(
                rs.getObject("settlement_id", UUID.class),
                rs.getObject("prediction_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("market_id", UUID.class),
                rs.getTimestamp("accepted_at").toInstant(),
                SettlementState.valueOf(rs.getString("state")),
                rs.getString("selection"),
                rs.getBigDecimal("accepted_odds")));
  }
}
