package br.com.betufu.odds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Minimal public odds snapshot consumed by the betting module. */
public interface OddsForPredictionPort {
  record Selection(String key, String label, BigDecimal odds) {}

  record MarketSnapshot(
      UUID marketId,
      UUID eventId,
      UUID competitionId,
      String state,
      UUID marketVersionId,
      UUID publicationId,
      String ruleVersion,
      Instant closeDeadline,
      List<Selection> selections) {
    public MarketSnapshot {
      selections = selections == null ? List.of() : List.copyOf(selections);
    }
  }

  MarketSnapshot getSnapshot(UUID marketId);
}
