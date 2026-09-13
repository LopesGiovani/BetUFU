package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionTicket;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class PredictionRepository {
  private final JdbcTemplate jdbc;

  public PredictionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<PredictionTicket> findByKey(UUID identityId, String key) {
    return jdbc.query(
        "SELECT prediction_id, market_id, market_version_id, publication_id, selection, stake, accepted_odds, potential_return, net_profit, rule_version, close_deadline, state, accepted_at FROM public.prediction WHERE identity_id = ? AND idempotency_key = ?",
        rs -> rs.next() ? Optional.of(ticket(rs)) : Optional.empty(), identityId, key);
  }

  public PredictionTicket insert(UUID identityId, UUID marketId, UUID marketVersionId, UUID publicationId,
      String ruleVersion, String selection, BigDecimal stake, BigDecimal odds, BigDecimal potentialReturn,
      BigDecimal netProfit, Instant closeDeadline, String key, String digest) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, identityId, marketId, marketVersionId, publicationId, ruleVersion, selection, stake, odds, potentialReturn, netProfit, Timestamp.from(closeDeadline), key, digest);
    jdbc.update("INSERT INTO public.prediction_selection (prediction_id, selection_no, selection_key) VALUES (?, 1, ?)", id, selection);
    return findByKey(identityId, key).orElseThrow();
  }

  private static PredictionTicket ticket(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PredictionTicket(rs.getObject("prediction_id", UUID.class), rs.getObject("market_id", UUID.class), rs.getObject("market_version_id", UUID.class), rs.getObject("publication_id", UUID.class), rs.getString("selection"), rs.getBigDecimal("stake"), rs.getBigDecimal("accepted_odds"), rs.getBigDecimal("potential_return"), rs.getBigDecimal("net_profit"), rs.getString("rule_version"), rs.getTimestamp("close_deadline").toInstant(), rs.getString("state"), rs.getTimestamp("accepted_at").toInstant());
  }
}
