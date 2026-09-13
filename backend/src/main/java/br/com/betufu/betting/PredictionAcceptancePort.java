package br.com.betufu.betting;

import java.math.BigDecimal;
import java.util.UUID;

/** Public command boundary for accepting one pre-game prediction. */
public interface PredictionAcceptancePort {
  record SubmitPrediction(
      UUID marketId,
      UUID marketVersionId,
      UUID publicationId,
      String ruleVersion,
      String selection,
      BigDecimal stake,
      String idempotencyKey) {}

  PredictionTicket accept(UUID identityId, SubmitPrediction command);
}
