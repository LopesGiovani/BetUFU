package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OddsPublicPrivacyIT {
  @Test
  void publicProjectionDoesNotDeclareOperationalActorOrEvidenceFields() {
    assertThat(
            Arrays.stream(PublicOddsView.class.getRecordComponents()).map(RecordComponent::getName))
        .doesNotContain("actorId", "matricula", "hmac", "evidence", "notes");
  }
}
