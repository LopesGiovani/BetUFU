package br.com.betufu.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consumer-owned accepted-ticket view without account or enrollment details. */
public interface PredictionForSettlementPort {
  List<PredictionSnapshot> findAcceptedByEvent(UUID eventId);

  record PredictionSnapshot(
      UUID predictionId,
      UUID identityId,
      UUID eventId,
      UUID marketId,
      PredictionState state,
      String selection,
      BigDecimal stake,
      BigDecimal acceptedOdds,
      BigDecimal potentialReturn,
      String ruleVersion,
      Instant acceptedAt) {}

  enum PredictionState {
    PENDING,
    ANNULLED
  }
}
