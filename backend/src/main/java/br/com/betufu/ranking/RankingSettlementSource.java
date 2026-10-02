package br.com.betufu.ranking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consumer-owned view of the latest complete, decisive settlement for each prediction. */
public interface RankingSettlementSource {
  enum Decision {
    WON,
    LOST
  }

  record DecisivePrediction(
      UUID identityId, UUID marketId, Instant acceptedAt, Decision decision) {}

  List<DecisivePrediction> findCurrentDecisions();
}
