package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.betting.internal.PredictionEligibilityPolicy;
import br.com.betufu.identity.OperationalRole;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.verification.EnrollmentEligibility;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PredictionConflictIT {
  @Test
  void rejectsOnlyTheCompetitionScopeCarriedByTheAuthoritativeSnapshot() {
    UUID identity = UUID.randomUUID();
    UUID blockedCompetition = UUID.randomUUID();
    UUID otherCompetition = UUID.randomUUID();
    EnrollmentEligibility enrollment =
        new EnrollmentEligibility() {
          @Override
          public boolean hasActiveEnrollment(UUID identityId) {
            return true;
          }

          @Override
          public Status getStatus(UUID identityId) {
            return Status.ATIVO;
          }
        };
    OperationalRoleQuery roles =
        ignored ->
            Set.of(
                new OperationalRoleQuery.GrantedRole(
                    OperationalRole.ADMINISTRADOR_COMPETICAO, blockedCompetition));
    PredictionEligibilityPolicy policy = new PredictionEligibilityPolicy(enrollment, roles);

    assertThat(policy.isEligible(identity, blockedCompetition)).isFalse();
    assertThat(policy.isEligible(identity, otherCompetition)).isTrue();
  }
}
