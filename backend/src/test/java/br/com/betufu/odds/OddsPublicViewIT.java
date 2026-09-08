package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsExplanation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OddsPublicViewIT {
  @Test
  void explanationUsesFairProbabilityAndMarginAsSeparatePublicFields() {
    PublicOddsExplanation explanation =
        new PublicOddsExplanation(
            "elo-davidson-v1",
            "sha256:test",
            new BigDecimal("0.5"),
            new BigDecimal("0.05"),
            "HALF_UP",
            new BigDecimal("0.2"),
            "LOW",
            0);
    assertThat(explanation.fairProbability()).isEqualByComparingTo("0.5");
    assertThat(explanation.margin()).isEqualByComparingTo("0.05");
  }
}
