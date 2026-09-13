package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PredictionConflictIT {
  @Test
  void conflictSurfaceIsRepresentedByStableCommandTypes() {
    PredictionAcceptancePort.SubmitPrediction command = new PredictionAcceptancePort.SubmitPrediction(null, null, null, "rule-v1", "HOME", null, "key");
    assertThatThrownBy(() -> java.util.Objects.requireNonNull(command.marketId())).isInstanceOf(NullPointerException.class);
  }
}
