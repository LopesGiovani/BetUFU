package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.betufu.identity.ProfileEnrollmentStatusLookup;
import br.com.betufu.verification.internal.ProfileEnrollmentStatusAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileEnrollmentStatusAdapterTest {

  @Mock private EnrollmentEligibility enrollmentEligibility;

  @InjectMocks private ProfileEnrollmentStatusAdapter adapter;

  @Test
  void activeEnrollmentIsProjectedAsRegistered() {
    UUID identityId = UUID.randomUUID();
    when(enrollmentEligibility.hasActiveEnrollment(identityId)).thenReturn(true);

    assertThat(adapter.findStatus(identityId))
        .isEqualTo(ProfileEnrollmentStatusLookup.Status.REGISTRADA);
    verify(enrollmentEligibility).hasActiveEnrollment(identityId);
  }

  @Test
  void missingActiveEnrollmentIsProjectedAsNotRegistered() {
    UUID identityId = UUID.randomUUID();
    when(enrollmentEligibility.hasActiveEnrollment(identityId)).thenReturn(false);

    assertThat(adapter.findStatus(identityId))
        .isEqualTo(ProfileEnrollmentStatusLookup.Status.NAO_REGISTRADA);
    verify(enrollmentEligibility).hasActiveEnrollment(identityId);
  }

  @Test
  void nullIdentityFailsInsteadOfSilentlyClaimingNoEnrollment() {
    assertThatThrownBy(() -> adapter.findStatus(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("identityId must not be null");
    verifyNoInteractions(enrollmentEligibility);
  }
}
