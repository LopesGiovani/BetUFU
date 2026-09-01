package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.verification.internal.EnrollmentService;
import br.com.betufu.verification.internal.UfuEnrollment;
import br.com.betufu.verification.internal.UfuEnrollmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrollmentEligibilityTest {

  private UfuEnrollmentRepository enrollmentRepository;
  private EnrollmentEligibility eligibility;

  @BeforeEach
  void setUp() {
    enrollmentRepository = mock(UfuEnrollmentRepository.class);
    eligibility = new EnrollmentService(enrollmentRepository, null, null, null);
  }

  @Test
  @DisplayName(
      "VERI-09: Identidade sem nenhum vínculo registrado não é elegível (hasActiveEnrollment = false, status = SEM_VINCULO)")
  void identityWithoutEnrollmentIsNotEligible() {
    UUID identityId = UUID.randomUUID();
    when(enrollmentRepository.findActiveByIdentityId(identityId)).thenReturn(Optional.empty());
    when(enrollmentRepository.findAllByIdentityId(identityId)).thenReturn(List.of());

    assertThat(eligibility.hasActiveEnrollment(identityId)).isFalse();
    assertThat(eligibility.getStatus(identityId))
        .isEqualTo(EnrollmentEligibility.Status.SEM_VINCULO);
  }

  @Test
  @DisplayName(
      "VERI-09: Identidade com vínculo ativo é elegível (hasActiveEnrollment = true, status = ATIVO)")
  void identityWithActiveEnrollmentIsEligible() {
    UUID identityId = UUID.randomUUID();
    UfuEnrollment activeEnrollment =
        new UfuEnrollment(
            UUID.randomUUID(),
            identityId,
            new byte[32],
            (short) 1,
            UfuEnrollment.STATUS_ATIVO,
            Instant.now(),
            Instant.now());

    when(enrollmentRepository.findActiveByIdentityId(identityId))
        .thenReturn(Optional.of(activeEnrollment));
    when(enrollmentRepository.findAllByIdentityId(identityId))
        .thenReturn(List.of(activeEnrollment));

    assertThat(eligibility.hasActiveEnrollment(identityId)).isTrue();
    assertThat(eligibility.getStatus(identityId)).isEqualTo(EnrollmentEligibility.Status.ATIVO);
  }

  @Test
  @DisplayName(
      "VERI-09 / D-32: Identidade com vínculo liberado não é elegível (hasActiveEnrollment = false, status = LIBERADO)")
  void identityWithReleasedEnrollmentIsNotEligible() {
    UUID identityId = UUID.randomUUID();
    UfuEnrollment releasedEnrollment =
        new UfuEnrollment(
            UUID.randomUUID(),
            identityId,
            new byte[32],
            (short) 1,
            UfuEnrollment.STATUS_LIBERADO,
            Instant.now().minusSeconds(3600),
            Instant.now());

    when(enrollmentRepository.findActiveByIdentityId(identityId)).thenReturn(Optional.empty());
    when(enrollmentRepository.findAllByIdentityId(identityId))
        .thenReturn(List.of(releasedEnrollment));

    assertThat(eligibility.hasActiveEnrollment(identityId)).isFalse();
    assertThat(eligibility.getStatus(identityId)).isEqualTo(EnrollmentEligibility.Status.LIBERADO);
  }
}
