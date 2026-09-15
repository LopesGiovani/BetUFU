package br.com.betufu.odds.internal.prediction;

import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter that exposes only the public odds snapshot needed by betting. */
@Component
@Profile("runtime | integrated")
public class OddsForPredictionAdapter implements OddsForPredictionPort {
  private final JdbcTemplate jdbc;

  public OddsForPredictionAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  @Transactional
  public MarketSnapshot getSnapshot(UUID marketId) {
    Objects.requireNonNull(marketId, "marketId must not be null");
    List<LockedMarket> markets =
        jdbc.query(
            "SELECT m.market_id, m.event_id, e.competition_id, m.current_version_id, v.state, v.close_deadline FROM public.odds_market m JOIN public.sports_event e ON e.event_id = m.event_id JOIN public.odds_market_version v ON v.market_version_id = m.current_version_id AND v.market_id = m.market_id WHERE m.market_id = ? FOR UPDATE OF m",
            (rs, rowNum) ->
                new LockedMarket(
                    rs.getObject("market_id", UUID.class),
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("current_version_id", UUID.class),
                    rs.getString("state"),
                    rs.getTimestamp("close_deadline") == null
                        ? null
                        : rs.getTimestamp("close_deadline").toInstant()),
            marketId);
    if (markets.size() != 1) throw unavailable();
    LockedMarket market = markets.getFirst();

    List<PublicationCandidate> candidates =
        jdbc.query(
            "WITH RECURSIVE lineage AS ("
                + " SELECT v.market_version_id, v.market_id, v.supersedes_version_id, 0 AS depth, ARRAY[v.market_version_id] AS path, false AS cycle"
                + " FROM public.odds_market_version v WHERE v.market_version_id = ? AND v.market_id = ?"
                + " UNION ALL"
                + " SELECT predecessor.market_version_id, predecessor.market_id, predecessor.supersedes_version_id, lineage.depth + 1, lineage.path || predecessor.market_version_id, predecessor.market_version_id = ANY(lineage.path)"
                + " FROM lineage JOIN public.odds_market_version predecessor ON predecessor.market_version_id = lineage.supersedes_version_id AND predecessor.market_id = lineage.market_id"
                + " WHERE NOT lineage.cycle"
                + "), candidates AS ("
                + " SELECT lineage.depth, p.publication_id, c.algorithm_version"
                + " FROM lineage JOIN public.odds_calculation c ON c.market_version_id = lineage.market_version_id"
                + " JOIN public.odds_publication p ON p.calculation_id = c.calculation_id"
                + "), lineage_status AS (SELECT COALESCE(bool_or(cycle), false) AS cycle FROM lineage)"
                + " SELECT candidates.publication_id, candidates.algorithm_version, lineage_status.cycle"
                + " FROM candidates CROSS JOIN lineage_status"
                + " WHERE candidates.depth = (SELECT min(depth) FROM candidates)",
            (rs, rowNum) ->
                new PublicationCandidate(
                    rs.getObject("publication_id", UUID.class),
                    rs.getString("algorithm_version"),
                    rs.getBoolean("cycle")),
            market.currentVersionId(),
            market.marketId());
    if (candidates.size() != 1 || candidates.getFirst().cycle()) throw unavailable();
    PublicationCandidate publication = candidates.getFirst();
    List<Selection> selections = loadSelections(publication.publicationId());

    return new MarketSnapshot(
        market.marketId(),
        market.eventId(),
        market.competitionId(),
        market.state(),
        market.currentVersionId(),
        publication.publicationId(),
        publication.algorithmVersion(),
        market.closeDeadline(),
        selections);
  }

  private List<Selection> loadSelections(UUID publicationId) {
    List<StoredSelection> stored =
        jdbc.query(
            "SELECT selection_no, selection_key, published_odds FROM public.odds_publication_selection WHERE publication_id = ? ORDER BY selection_no",
            (rs, rowNum) ->
                new StoredSelection(
                    rs.getInt("selection_no"),
                    rs.getString("selection_key"),
                    rs.getBigDecimal("published_odds")),
            publicationId);
    boolean valid =
        (stored.size() == 2 && stored.get(0).matches(1, "HOME") && stored.get(1).matches(2, "AWAY"))
            || (stored.size() == 3
                && stored.get(0).matches(1, "HOME")
                && stored.get(1).matches(2, "DRAW")
                && stored.get(2).matches(3, "AWAY"));
    if (!valid || stored.stream().anyMatch(selection -> selection.odds().signum() <= 0)) {
      throw unavailable();
    }
    return stored.stream()
        .map(selection -> new Selection(selection.key(), label(selection.key()), selection.odds()))
        .toList();
  }

  private static String label(String key) {
    return switch (key) {
      case "HOME" -> "Mandante";
      case "DRAW" -> "Empate";
      case "AWAY" -> "Visitante";
      default -> throw unavailable();
    };
  }

  private static IllegalStateException unavailable() {
    return new IllegalStateException("odds snapshot is unavailable");
  }

  private record LockedMarket(
      UUID marketId,
      UUID eventId,
      UUID competitionId,
      UUID currentVersionId,
      String state,
      Instant closeDeadline) {}

  private record PublicationCandidate(UUID publicationId, String algorithmVersion, boolean cycle) {}

  private record StoredSelection(int selectionNo, String key, BigDecimal odds) {
    private boolean matches(int expectedNo, String expectedKey) {
      return selectionNo == expectedNo && expectedKey.equals(key);
    }
  }
}
