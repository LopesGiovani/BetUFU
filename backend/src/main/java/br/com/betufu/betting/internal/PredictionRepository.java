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
  private static final String TICKET_COLUMNS =
      "prediction_id, market_id, market_version_id, publication_id, selection, stake, accepted_odds, potential_return, net_profit, rule_version, close_deadline, state, accepted_at";
  private final JdbcTemplate jdbc;

  public PredictionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<PredictionTicket> findByKey(UUID identityId, String key) {
    return findReceiptByKey(identityId, key).map(StoredPrediction::ticket);
  }

  public void lockIdempotency(UUID identityId, String key) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtext(CAST(? AS text)), hashtext(CAST(? AS text)))",
        rs -> {
          if (!rs.next()) {
            throw new IllegalStateException("prediction idempotency lock unavailable");
          }
          return null;
        },
        identityId.toString(),
        key);
  }

  public Optional<StoredPrediction> findReceiptByKey(UUID identityId, String key) {
    return jdbc.query(
        "SELECT "
            + TICKET_COLUMNS
            + ", payload_digest FROM public.prediction WHERE identity_id = ? AND idempotency_key = ?",
        rs ->
            rs.next()
                ? Optional.of(new StoredPrediction(ticket(rs), rs.getString("payload_digest")))
                : Optional.empty(),
        identityId,
        key);
  }

  public Optional<PredictionTicket> findByMarket(UUID identityId, UUID marketId) {
    return jdbc.query(
        "SELECT "
            + TICKET_COLUMNS
            + " FROM public.prediction WHERE identity_id = ? AND market_id = ? ORDER BY accepted_at DESC LIMIT 1",
        rs -> rs.next() ? Optional.of(ticket(rs)) : Optional.empty(),
        identityId,
        marketId);
  }

  public PredictionTicket insert(
      UUID id,
      UUID identityId,
      UUID marketId,
      UUID marketVersionId,
      UUID publicationId,
      String ruleVersion,
      String selection,
      BigDecimal stake,
      BigDecimal odds,
      BigDecimal potentialReturn,
      BigDecimal netProfit,
      Instant closeDeadline,
      String key,
      String digest) {
    jdbc.update(
        "INSERT INTO public.prediction (prediction_id, identity_id, market_id, market_version_id, publication_id, rule_version, selection, stake, accepted_odds, potential_return, net_profit, close_deadline, idempotency_key, payload_digest) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        identityId,
        marketId,
        marketVersionId,
        publicationId,
        ruleVersion,
        selection,
        stake,
        odds,
        potentialReturn,
        netProfit,
        Timestamp.from(closeDeadline),
        key,
        digest);
    jdbc.update(
        "INSERT INTO public.prediction_selection (prediction_id, selection_no, selection_key) VALUES (?, 1, ?)",
        id,
        selection);
    return findByKey(identityId, key).orElseThrow();
  }

  private static PredictionTicket ticket(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PredictionTicket(
        rs.getObject("prediction_id", UUID.class),
        rs.getObject("market_id", UUID.class),
        rs.getObject("market_version_id", UUID.class),
        rs.getObject("publication_id", UUID.class),
        rs.getString("selection"),
        rs.getBigDecimal("stake"),
        rs.getBigDecimal("accepted_odds"),
        rs.getBigDecimal("potential_return"),
        rs.getBigDecimal("net_profit"),
        rs.getString("rule_version"),
        rs.getTimestamp("close_deadline").toInstant(),
        rs.getString("state"),
        rs.getTimestamp("accepted_at").toInstant());
  }

  public record StoredPrediction(PredictionTicket ticket, String payloadDigest) {}
}
