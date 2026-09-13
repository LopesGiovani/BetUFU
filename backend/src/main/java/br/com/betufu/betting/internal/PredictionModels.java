package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionAcceptancePort.SubmitPrediction;
import java.math.BigDecimal;
import java.util.UUID;

public final class PredictionModels {
  private PredictionModels() {}
  public record PredictionSubmitRequest(UUID marketId, UUID marketVersionId, UUID publicationId, String ruleVersion, String selection, BigDecimal stake) {
    public SubmitPrediction toCommand(String key) { return new SubmitPrediction(marketId, marketVersionId, publicationId, ruleVersion, selection, stake, key); }
  }
}
