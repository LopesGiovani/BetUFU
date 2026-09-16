package br.com.betufu.verification.internal;

import br.com.betufu.identity.ProfileEnrollmentStatusLookup;
import br.com.betufu.verification.EnrollmentEligibility;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Projects verification state through the minimal contract owned by the identity module. */
@Component
@Profile("runtime | integrated")
public class ProfileEnrollmentStatusAdapter implements ProfileEnrollmentStatusLookup {

  private final EnrollmentEligibility enrollmentEligibility;

  public ProfileEnrollmentStatusAdapter(EnrollmentEligibility enrollmentEligibility) {
    this.enrollmentEligibility =
        Objects.requireNonNull(enrollmentEligibility, "enrollmentEligibility must not be null");
  }

  @Override
  public Status findStatus(UUID identityId) {
    Objects.requireNonNull(identityId, "identityId must not be null");
    return enrollmentEligibility.hasActiveEnrollment(identityId)
        ? Status.REGISTRADA
        : Status.NAO_REGISTRADA;
  }
}
