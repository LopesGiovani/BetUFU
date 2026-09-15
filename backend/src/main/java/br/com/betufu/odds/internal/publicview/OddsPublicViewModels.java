package br.com.betufu.odds.internal.publicview;

import br.com.betufu.odds.internal.market.MarketState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Allowlisted anonymous projection; operational actors and raw evidence never cross this DTO. */
public final class OddsPublicViewModels {
  private OddsPublicViewModels() {}

  public record PublicOddsView(
      UUID marketId,
      UUID marketVersionId,
      UUID eventId,
      String marketType,
      MarketState state,
      Instant closeDeadline,
      PublicOddsSummary current,
      List<PublicOddsHistory> history,
      PublicOddsExplanation explanation) {
    public PublicOddsView {
      history = history == null ? List.of() : List.copyOf(history);
    }
  }

  public record PublicOddsSummary(
      UUID publicationId, List<BigDecimal> publishedOdds, Instant publishedAt) {
    public PublicOddsSummary {
      publishedOdds = publishedOdds == null ? List.of() : List.copyOf(publishedOdds);
    }
  }

  public record PublicOddsHistory(
      UUID publicationId, BigDecimal publishedOdds, Instant publishedAt) {}

  public record PublicOddsExplanation(
      String algorithmVersion,
      String configurationVersion,
      BigDecimal fairProbability,
      BigDecimal margin,
      String roundingMode,
      BigDecimal confidence,
      String confidenceBand,
      int sampleSize) {}
}
