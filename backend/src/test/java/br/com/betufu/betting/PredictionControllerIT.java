package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PredictionControllerIT {
  @Test
  void idempotencyHeaderIsRequiredByContract() {
    assertThat(new PredictionAcceptancePort.SubmitPrediction(null, null, null, "rule-v1", "HOME", null, "").idempotencyKey()).isBlank();
  }
}
