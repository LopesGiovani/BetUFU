package br.com.betufu.betting.internal;

import br.com.betufu.identity.OperationalRole;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.ledger.CreditCorrectionPort;
import br.com.betufu.verification.EnrollmentEligibility;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PredictionEligibilityPolicy {
  private final EnrollmentEligibility eligibility;
  private final OperationalRoleQuery roles;
  private final CreditCorrectionPort corrections;

  public PredictionEligibilityPolicy(
      EnrollmentEligibility eligibility, OperationalRoleQuery roles) {
    this(eligibility, roles, null);
  }

  @Autowired
  public PredictionEligibilityPolicy(
      EnrollmentEligibility eligibility,
      OperationalRoleQuery roles,
      CreditCorrectionPort corrections) {
    this.eligibility = eligibility;
    this.roles = roles;
    this.corrections = corrections;
  }

  public boolean isEligible(UUID identityId, UUID competitionId) {
    if (!eligibility.hasActiveEnrollment(identityId)) return false;
    if (roles.hasRole(identityId, OperationalRole.ADMINISTRADOR_GERAL)) return false;
    if (corrections != null && corrections.hasPending(identityId)) return false;
    return competitionId == null
        || !roles.hasRole(identityId, OperationalRole.ADMINISTRADOR_COMPETICAO, competitionId);
  }
}
