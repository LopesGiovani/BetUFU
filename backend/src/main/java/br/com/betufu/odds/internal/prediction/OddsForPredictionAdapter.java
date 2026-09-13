package br.com.betufu.odds.internal.prediction;

import br.com.betufu.odds.OddsForPredictionPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC adapter that exposes only the public odds snapshot needed by betting. */
@Component
@Profile("runtime | integrated")
public class OddsForPredictionAdapter implements OddsForPredictionPort {
  private final JdbcTemplate jdbc;

  public OddsForPredictionAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public MarketSnapshot getSnapshot(UUID marketId) {
    return jdbc.queryForObject(
        "SELECT m.market_id, m.event_id, m.current_version_id, v.state, v.close_deadline, p.publication_id, p.published_odds FROM public.odds_market m JOIN public.odds_market_version v ON v.market_version_id = m.current_version_id LEFT JOIN public.odds_calculation c ON c.market_version_id = v.market_version_id LEFT JOIN public.odds_publication p ON p.calculation_id = c.calculation_id WHERE m.market_id = ? ORDER BY p.published_at DESC NULLS LAST LIMIT 1",
        (rs, rowNum) ->
            new MarketSnapshot(
                rs.getObject("market_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                null,
                rs.getString("state"),
                rs.getObject("current_version_id", UUID.class),
                rs.getObject("publication_id", UUID.class),
                "odds-v1",
                rs.getTimestamp("close_deadline") == null ? null : rs.getTimestamp("close_deadline").toInstant(),
                java.util.List.of(new Selection("HOME", "Mandante", rs.getBigDecimal("published_odds")) )),
        marketId);
  }
}
