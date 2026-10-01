package br.com.betufu.betting.internal;

import br.com.betufu.settlement.PredictionForSettlementPort;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PredictionForSettlementAdapter implements PredictionForSettlementPort {
  private final JdbcTemplate jdbc;

  public PredictionForSettlementAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PredictionSnapshot> findAcceptedByEvent(UUID eventId) {
    if (eventId == null) return List.of();
    return jdbc.query(
        """
            SELECT p.prediction_id, p.identity_id, m.event_id, p.market_id, p.state, p.selection,
                   p.stake, p.accepted_odds, p.potential_return, p.rule_version, p.accepted_at
            FROM public.prediction p
            JOIN public.odds_market m ON m.market_id = p.market_id
            WHERE m.event_id = ? AND p.state IN ('PENDING', 'ANNULLED')
            ORDER BY p.accepted_at, p.prediction_id
            """,
        (rs, rowNum) ->
            new PredictionSnapshot(
                rs.getObject("prediction_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("market_id", UUID.class),
                PredictionState.valueOf(rs.getString("state")),
                rs.getString("selection"),
                rs.getBigDecimal("stake"),
                rs.getBigDecimal("accepted_odds"),
                rs.getBigDecimal("potential_return"),
                rs.getString("rule_version"),
                rs.getTimestamp("accepted_at").toInstant()),
        eventId);
  }
}
