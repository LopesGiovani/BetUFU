package br.com.betufu.settlement.internal;

import br.com.betufu.ranking.RankingSettlementSource;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Exposes only current, fully processed decisions; incomplete corrections hide older results. */
@Component
@Profile("runtime | integrated")
public class RankingSettlementAdapter implements RankingSettlementSource {
  private final JdbcTemplate jdbc;

  public RankingSettlementAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<DecisivePrediction> findCurrentDecisions() {
    return jdbc.query(
        """
            SELECT u.identity_id, p.market_id, p.accepted_at, u.state
            FROM public.prediction_settlement u
            JOIN public.settlement_case c ON c.case_id = u.case_id
            JOIN public.settlement_processing progress
              ON progress.case_id = c.case_id AND progress.status = 'COMPLETE'
            JOIN public.prediction p ON p.prediction_id = u.prediction_id
            WHERE u.state IN ('WON', 'LOST')
              AND NOT EXISTS (
                SELECT 1 FROM public.settlement_case newer
                WHERE newer.event_id = c.event_id
                  AND newer.processing_version > c.processing_version
              )
            """,
        (rs, row) ->
            new DecisivePrediction(
                rs.getObject("identity_id", java.util.UUID.class),
                rs.getObject("market_id", java.util.UUID.class),
                rs.getTimestamp("accepted_at").toInstant(),
                Decision.valueOf(rs.getString("state"))));
  }
}
