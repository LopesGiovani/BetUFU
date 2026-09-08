package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.AdministrativeOutcomeRequest;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeKind;
import org.junit.jupiter.api.Test;

class SportsAdministrativeOutcomeIT {
  @Test
  void administrativeRequestCarriesReasonAndNoScoreFields() {
    AdministrativeOutcomeRequest request =
        new AdministrativeOutcomeRequest(
            OutcomeKind.ADMINISTRATIVE_WALKOVER, "W.O. confirmado", "https://source.invalid", 1);
    assertThat(request.outcomeKind()).isEqualTo(OutcomeKind.ADMINISTRATIVE_WALKOVER);
    assertThat(request.reason()).isEqualTo("W.O. confirmado");
    assertThat(request).hasNoNullFieldsOrPropertiesExcept("winnerSlotNo");
  }
}
