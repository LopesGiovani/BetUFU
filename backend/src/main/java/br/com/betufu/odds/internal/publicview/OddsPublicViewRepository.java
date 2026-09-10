package br.com.betufu.odds.internal.publicview;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsExplanation;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsHistory;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsSummary;
import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class OddsPublicViewRepository {
  private final JdbcTemplate jdbc;

  public OddsPublicViewRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<PublicOddsView> find(UUID marketId) {
    if (marketId == null) return Optional.empty();
    List<Header> headers =
        jdbc.query(
            "SELECT m.market_id, m.event_id, m.market_type, v.state, v.market_version_id FROM public.odds_market m JOIN public.odds_market_version v ON v.market_version_id = m.current_version_id WHERE m.market_id = ?",
            (rs, n) ->
                new Header(
                    rs.getObject("market_id", UUID.class),
                    rs.getObject("event_id", UUID.class),
                    rs.getString("market_type"),
                    state(rs.getString("state")),
                    rs.getObject("market_version_id", UUID.class)),
            marketId);
    if (headers.isEmpty()) return Optional.empty();
    Header h = headers.get(0);
    List<PublicationRow> rows =
        jdbc.query(
            "SELECT p.publication_id, p.published_odds, p.published_at, p.margin, p.rounding_mode, c.algorithm_version, c.configuration_digest, c.fair_probability, c.confidence, c.input_snapshot ->> 'confidenceBand' AS confidence_band, CAST(c.input_snapshot ->> 'sampleSize' AS integer) AS sample_size FROM public.odds_publication p JOIN public.odds_calculation c ON c.calculation_id = p.calculation_id JOIN public.odds_market_version v ON v.market_version_id = c.market_version_id WHERE v.market_id = ? ORDER BY p.published_at DESC, p.publication_id DESC",
            (rs, n) ->
                new PublicationRow(
                    rs.getObject("publication_id", UUID.class),
                    rs.getBigDecimal("published_odds"),
                    rs.getTimestamp("published_at").toInstant(),
                    rs.getString("margin"),
                    rs.getString("rounding_mode"),
                    rs.getString("algorithm_version"),
                    rs.getString("configuration_digest"),
                    rs.getBigDecimal("fair_probability"),
                    rs.getBigDecimal("confidence"),
                    rs.getString("confidence_band"),
                    rs.getObject("sample_size", Integer.class)),
            marketId);
    PublicOddsSummary current =
        rows.isEmpty()
            ? null
            : new PublicOddsSummary(
                rows.get(0).publicationId(),
                List.of(rows.get(0).publishedOdds()),
                rows.get(0).publishedAt());
    List<PublicOddsHistory> history =
        rows.stream()
            .map(r -> new PublicOddsHistory(r.publicationId(), r.publishedOdds(), r.publishedAt()))
            .toList();
    PublicOddsExplanation explanation =
        rows.isEmpty()
            ? null
            : new PublicOddsExplanation(
                rows.get(0).algorithmVersion(),
                rows.get(0).configurationDigest(),
                rows.get(0).fairProbability(),
                new BigDecimal(rows.get(0).margin()),
                rows.get(0).roundingMode(),
                rows.get(0).confidence(),
                rows.get(0).confidenceBand(),
                rows.get(0).sampleSize() == null ? 0 : rows.get(0).sampleSize());
    return Optional.of(
        new PublicOddsView(
            h.marketId(), h.eventId(), h.marketType(), h.state(), current, history, explanation));
  }

  private static MarketState state(String value) {
    return "VOID".equals(value) ? MarketState.CANCELLED : MarketState.valueOf(value);
  }

  private record Header(
      UUID marketId, UUID eventId, String marketType, MarketState state, UUID versionId) {}

  private record PublicationRow(
      UUID publicationId,
      BigDecimal publishedOdds,
      java.time.Instant publishedAt,
      String margin,
      String roundingMode,
      String algorithmVersion,
      String configurationDigest,
      BigDecimal fairProbability,
      BigDecimal confidence,
      String confidenceBand,
      Integer sampleSize) {}
}
