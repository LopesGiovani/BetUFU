package br.com.betufu.settlement.internal;

import br.com.betufu.chat.ChatSettlementPort;
import br.com.betufu.settlement.PredictionForSettlementPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatSettlementAdapter implements ChatSettlementPort {
  private final JdbcTemplate jdbc;
  private final RecoveryPort recoveries;
  private final PredictionForSettlementPort predictions;

  public ChatSettlementAdapter(
      JdbcTemplate jdbc, RecoveryPort recoveries, PredictionForSettlementPort predictions) {
    this.jdbc = jdbc;
    this.recoveries = recoveries;
    this.predictions = predictions;
  }

  @Override
  public Optional<Completion> findCompletion(UUID eventId, UUID outcomeFactId) {
    if (eventId == null || outcomeFactId == null) return Optional.empty();
    return jdbc.query(
        """
        SELECT c.case_id,p.status,p.total_units,p.completed_units,f.completed_at
        FROM public.settlement_case c
        JOIN public.settlement_processing p ON p.case_id=c.case_id
        LEFT JOIN public.settlement_chat_completion_fact f ON f.case_id=c.case_id
        WHERE c.event_id=? AND c.outcome_fact_id=?
        ORDER BY c.processing_version DESC LIMIT 1
        """,
        rs -> {
          if (!rs.next()) return Optional.empty();
          var units =
              jdbc.query(
                  "SELECT settlement_id FROM public.prediction_settlement WHERE case_id=?",
                  (row, index) -> row.getObject(1, UUID.class),
                  rs.getObject("case_id", UUID.class));
          var recovery = recoveries.inspectRecoveries(units);
          Instant at =
              rs.getTimestamp("completed_at") == null
                  ? null
                  : rs.getTimestamp("completed_at").toInstant();
          if (at != null && recovery.completedAt() != null && recovery.completedAt().isAfter(at))
            at = recovery.completedAt();
          boolean complete =
              "COMPLETE".equals(rs.getString("status"))
                  && at != null
                  && !recovery.pending()
                  && rs.getInt("total_units") == rs.getInt("completed_units")
                  && units.size() == rs.getInt("total_units")
                  && units.size() == predictions.findAcceptedByEvent(eventId).size();
          return Optional.of(new Completion(eventId, outcomeFactId, at, complete));
        },
        eventId,
        outcomeFactId);
  }
}
