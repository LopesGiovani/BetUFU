package br.com.betufu.verification.internal;

import br.com.betufu.sports.EnrollmentFingerprintPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adapter implementing {@link EnrollmentFingerprintPort} using verification module internals. */
@Component
@Profile("runtime | integrated")
public class SportsEnrollmentFingerprintAdapter implements EnrollmentFingerprintPort {

  private final MatriculaHmac matriculaHmac;
  private final UfuEnrollmentRepository enrollmentRepository;

  public SportsEnrollmentFingerprintAdapter(
      MatriculaHmac matriculaHmac, UfuEnrollmentRepository enrollmentRepository) {
    this.matriculaHmac = Objects.requireNonNull(matriculaHmac, "matriculaHmac must not be null");
    this.enrollmentRepository =
        Objects.requireNonNull(enrollmentRepository, "enrollmentRepository must not be null");
  }

  @Override
  public Optional<Fingerprint> computeFingerprint(String rawMatricula) {
    if (rawMatricula == null || rawMatricula.isBlank()) {
      return Optional.empty();
    }
    String normalized = MatriculaNormalizer.normalize(rawMatricula);
    if (!MatriculaNormalizer.isValid(normalized)) {
      throw new IllegalArgumentException("Matrícula inválida ou fora do padrão aprovado");
    }
    byte[] hash = matriculaHmac.calculateCurrent(normalized);
    short version = EnrollmentService.parseHmacVersion(matriculaHmac.currentVersion());
    return Optional.of(new Fingerprint(hash, version));
  }

  @Override
  public Optional<Fingerprint> findActiveFingerprintByIdentityId(UUID identityId) {
    if (identityId == null) {
      return Optional.empty();
    }
    return enrollmentRepository
        .findActiveByIdentityId(identityId)
        .map(
            enrollment ->
                new Fingerprint(enrollment.getMatriculaHmac(), enrollment.getHmacVersion()));
  }

  @Override
  public List<UUID> findActiveIdentitiesByFingerprint(byte[] hash) {
    if (hash == null || hash.length != 32) {
      return List.of();
    }
    return enrollmentRepository.findActiveByMatriculaHmacIn(List.of(hash)).stream()
        .map(UfuEnrollment::getIdentityId)
        .toList();
  }
}
