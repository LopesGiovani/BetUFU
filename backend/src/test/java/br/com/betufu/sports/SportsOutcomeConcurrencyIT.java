package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeKind;
import org.junit.jupiter.api.Test;

class SportsOutcomeConcurrencyIT {
  @Test
  void outcomeKindsRemainDisjointFromSportsScores() {
    assertThat(OutcomeKind.values())
        .containsExactly(OutcomeKind.ADMINISTRATIVE_WALKOVER, OutcomeKind.ABANDONED_NO_VALID_RESULT);
  }
}
