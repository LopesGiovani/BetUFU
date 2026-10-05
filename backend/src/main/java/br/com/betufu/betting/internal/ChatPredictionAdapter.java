package br.com.betufu.betting.internal;

import br.com.betufu.chat.ChatPredictionPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatPredictionAdapter implements ChatPredictionPort {
  private final JdbcTemplate jdbc;

  public ChatPredictionAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public EventPredictions inspect(UUID eventId) {
    if (eventId == null) return new EventPredictions(0, 0);
    return jdbc.queryForObject(
        """
        SELECT count(DISTINCT m.market_id) AS markets,
               count(p.prediction_id) FILTER (WHERE p.state IN ('PENDING','ANNULLED')) AS accepted
        FROM public.odds_market m
        LEFT JOIN public.prediction p ON p.market_id = m.market_id
        WHERE m.event_id = ?
        """,
        (rs, row) -> new EventPredictions(rs.getInt("markets"), rs.getInt("accepted")),
        eventId);
  }
}
