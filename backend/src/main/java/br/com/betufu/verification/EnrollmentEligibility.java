package br.com.betufu.verification;

import java.util.UUID;

public interface EnrollmentEligibility {

  enum Status {
    SEM_VINCULO,
    ATIVO,
    LIBERADO
  }

  boolean hasActiveEnrollment(UUID identityId);

  Status getStatus(UUID identityId);
}
