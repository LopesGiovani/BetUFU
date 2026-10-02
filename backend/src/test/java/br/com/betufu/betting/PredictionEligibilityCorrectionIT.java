package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.ledger.CreditCorrectionPort;
import br.com.betufu.verification.EnrollmentEligibility;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PredictionEligibilityCorrectionIT {
  @Test
  void activeEnrollmentCannotPlaceNewBetWhileCorrectionIsPending() {
    UUID identityId = UUID.randomUUID();
    UUID competitionId = UUID.randomUUID();
    EnrollmentEligibility enrollment = mock(EnrollmentEligibility.class);
    CreditCorrectionPort corrections = mock(CreditCorrectionPort.class);
    OperationalRoleQuery roles = ignored -> Set.of();
    when(enrollment.hasActiveEnrollment(identityId)).thenReturn(true);
    when(corrections.hasPending(identityId)).thenReturn(true, false);

    PredictionEligibilityPolicy policy =
        new PredictionEligibilityPolicy(enrollment, roles, corrections);
    assertThat(policy.isEligible(identityId, competitionId)).isFalse();
    assertThat(policy.isEligible(identityId, competitionId)).isTrue();
  }
}
