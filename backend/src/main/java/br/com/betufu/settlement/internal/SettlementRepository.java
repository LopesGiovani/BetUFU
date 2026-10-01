package br.com.betufu.settlement.internal;

import br.com.betufu.settlement.SettlementModels.Authorization;
import br.com.betufu.settlement.SettlementModels.SettlementCase;
import br.com.betufu.settlement.SettlementModels.SettlementReason;
import br.com.betufu.settlement.SettlementModels.Unit;
import br.com.betufu.settlement.SettlementState;
import java.sql.Timestamp;
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
}
