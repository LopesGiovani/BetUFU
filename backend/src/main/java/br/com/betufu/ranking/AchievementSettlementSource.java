package br.com.betufu.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consumer-owned view of current, complete settlement facts. */
public interface AchievementSettlementSource {
  enum Decision {
    WON,
    LOST
  }

  record CurrentFact(
      UUID settlementId,
      UUID predictionId,
      UUID identityId,
      UUID eventId,
      UUID marketId,
      Instant acceptedAt,
      Decision decision,
      String selection,
      BigDecimal acceptedOdds) {}

  List<CurrentFact> findCurrentFacts();
}
