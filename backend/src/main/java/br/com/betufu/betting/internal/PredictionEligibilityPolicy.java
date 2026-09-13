package br.com.betufu.betting.internal;

import br.com.betufu.identity.OperationalRole;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.verification.EnrollmentEligibility;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PredictionEligibilityPolicy {
  private final EnrollmentEligibility enrollment;
  private final OperationalRoleQuery roles;

  public PredictionEligibilityPolicy(EnrollmentEligibility enrollment, OperationalRoleQuery roles) {
    this.enrollment = enrollment;
    this.roles = roles;
  }

  public boolean isEligible(UUID identityId, UUID competitionId) {
    if (!enrollment.hasActiveEnrollment(identityId)) return false;
    if (roles.hasRole(identityId, OperationalRole.ADMINISTRADOR_GERAL)) return false;
    return competitionId == null || !roles.hasRole(identityId, OperationalRole.ADMINISTRADOR_COMPETICAO, competitionId);
  }
}
