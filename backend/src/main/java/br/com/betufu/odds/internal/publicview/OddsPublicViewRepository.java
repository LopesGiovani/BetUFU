package br.com.betufu.odds.internal.publicview;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsExplanation;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsHistory;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsSummary;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class OddsPublicViewRepository {
  private static final String LINEAGE_PUBLICATIONS_SQL =
      """
      WITH RECURSIVE lineage AS (
          SELECT v.market_version_id,
                 v.market_id,
                 v.supersedes_version_id,
                 0 AS depth,
                 ARRAY[v.market_version_id] AS path,
                 false AS cycle
            FROM public.odds_market_version v
           WHERE v.market_version_id = ?
             AND v.market_id = ?
          UNION ALL
          SELECT predecessor.market_version_id,
                 predecessor.market_id,
                 predecessor.supersedes_version_id,
                 lineage.depth + 1,
                 lineage.path || predecessor.market_version_id,
                 predecessor.market_version_id = ANY(lineage.path)
            FROM lineage
            JOIN public.odds_market_version predecessor
              ON predecessor.market_version_id = lineage.supersedes_version_id
             AND predecessor.market_id = lineage.market_id
           WHERE NOT lineage.cycle
      ), lineage_status AS (
          SELECT COALESCE(bool_or(cycle), false) AS cycle
            FROM lineage
      )
      SELECT p.publication_id,
             p.published_odds AS legacy_published_odds,
             array_agg(s.published_odds ORDER BY s.selection_no) AS published_odds,
             p.published_at,
             p.margin,
             p.rounding_mode,
             c.algorithm_version,
             c.configuration_digest,
             c.fair_probability,
             c.confidence,
             c.input_snapshot ->> 'confidenceBand' AS confidence_band,
             CAST(c.input_snapshot ->> 'sampleSize' AS integer) AS sample_size,
             lineage.depth AS lineage_depth,
             lineage_status.cycle AS lineage_cycle
        FROM lineage
        JOIN public.odds_calculation c
          ON c.market_version_id = lineage.market_version_id
        JOIN public.odds_publication p
          ON p.calculation_id = c.calculation_id
        JOIN public.odds_publication_selection s
          ON s.publication_id = p.publication_id
       CROSS JOIN lineage_status
       GROUP BY lineage.depth,
                lineage_status.cycle,
                p.publication_id,
                p.published_odds,
                p.published_at,
                p.margin,
                p.rounding_mode,
                c.algorithm_version,
                c.configuration_digest,
                c.fair_probability,
                c.confidence,
                c.input_snapshot
       ORDER BY lineage.depth ASC, p.published_at DESC, p.publication_id DESC
      """;

  private final JdbcTemplate jdbc;

  public OddsPublicViewRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UUID> findPublicMarketIdByEvent(UUID eventId) {
    if (eventId == null) return Optional.empty();
    List<UUID> publicMarketIds =
        jdbc
            .queryForList(
                "SELECT market_id FROM public.odds_market WHERE event_id = ? ORDER BY market_id",
                UUID.class,
                eventId)
            .stream()
            .filter(marketId -> find(marketId).isPresent())
            .toList();
    return publicMarketIds.size() == 1 ? Optional.of(publicMarketIds.getFirst()) : Optional.empty();
  }

  public Optional<PublicOddsView> find(UUID marketId) {
    if (marketId == null) return Optional.empty();
    List<Header> headers =
        jdbc.query(
            "SELECT m.market_id, m.event_id, m.market_type, v.state, v.market_version_id, v.close_deadline FROM public.odds_market m JOIN public.odds_market_version v ON v.market_version_id = m.current_version_id WHERE m.market_id = ?",
            (rs, n) ->
                new Header(
                    rs.getObject("market_id", UUID.class),
                    rs.getObject("event_id", UUID.class),
                    rs.getString("market_type"),
                    state(rs.getString("state")),
                    rs.getObject("market_version_id", UUID.class),
                    rs.getTimestamp("close_deadline") == null
                        ? null
                        : rs.getTimestamp("close_deadline").toInstant()),
            marketId);
    if (headers.isEmpty()) return Optional.empty();
    Header h = headers.get(0);
    List<PublicationRow> rows =
        jdbc.query(
            LINEAGE_PUBLICATIONS_SQL,
            (rs, n) ->
                new PublicationRow(
                    rs.getObject("publication_id", UUID.class),
                    publishedOdds(rs.getArray("published_odds")),
                    rs.getBigDecimal("legacy_published_odds"),
                    rs.getTimestamp("published_at").toInstant(),
                    rs.getString("margin"),
                    rs.getString("rounding_mode"),
                    rs.getString("algorithm_version"),
                    rs.getString("configuration_digest"),
                    rs.getBigDecimal("fair_probability"),
                    rs.getBigDecimal("confidence"),
                    rs.getString("confidence_band"),
                    rs.getObject("sample_size", Integer.class),
                    rs.getInt("lineage_depth"),
                    rs.getBoolean("lineage_cycle")),
            h.versionId(),
            marketId);
    if (h.closeDeadline() == null
        || rows.isEmpty()
        || rows.stream().anyMatch(PublicationRow::lineageCycle)) {
      return Optional.empty();
    }
    int nearestDepth = rows.getFirst().lineageDepth();
    List<PublicationRow> currentCandidates =
        rows.stream().filter(row -> row.lineageDepth() == nearestDepth).toList();
    if (currentCandidates.size() != 1) return Optional.empty();
    PublicationRow currentRow = currentCandidates.getFirst();
    PublicOddsSummary current =
        new PublicOddsSummary(
            currentRow.publicationId(), currentRow.publishedOdds(), currentRow.publishedAt());
    List<PublicOddsHistory> history =
        rows.stream()
            .map(
                r ->
                    new PublicOddsHistory(
                        r.publicationId(), r.legacyPublishedOdds(), r.publishedAt()))
            .toList();
    PublicOddsExplanation explanation =
        new PublicOddsExplanation(
            currentRow.algorithmVersion(),
            currentRow.configurationDigest(),
            currentRow.fairProbability(),
            new BigDecimal(currentRow.margin()),
            currentRow.roundingMode(),
            currentRow.confidence(),
            currentRow.confidenceBand(),
            currentRow.sampleSize() == null ? 0 : currentRow.sampleSize());
    return Optional.of(
        new PublicOddsView(
            h.marketId(),
            h.versionId(),
            h.eventId(),
            publicMarketType(h.marketType()),
            h.state(),
            h.closeDeadline(),
            current,
            history,
            explanation));
  }

  private static List<BigDecimal> publishedOdds(Array values) throws SQLException {
    if (values == null) return List.of();
    try {
      return Arrays.stream((Object[]) values.getArray()).map(BigDecimal.class::cast).toList();
    } finally {
      values.free();
    }
  }

  private static MarketState state(String value) {
    return "VOID".equals(value) ? MarketState.CANCELLED : MarketState.valueOf(value);
  }

  private static String publicMarketType(String value) {
    return "H2H".equals(value) ? "HEAD_TO_HEAD" : value;
  }

  private record Header(
      UUID marketId,
      UUID eventId,
      String marketType,
      MarketState state,
      UUID versionId,
      Instant closeDeadline) {}

  private record PublicationRow(
      UUID publicationId,
      List<BigDecimal> publishedOdds,
      BigDecimal legacyPublishedOdds,
      java.time.Instant publishedAt,
      String margin,
      String roundingMode,
      String algorithmVersion,
      String configurationDigest,
      BigDecimal fairProbability,
      BigDecimal confidence,
      String confidenceBand,
      Integer sampleSize,
      int lineageDepth,
      boolean lineageCycle) {}
}
