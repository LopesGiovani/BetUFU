package br.com.betufu.verification.internal;

import java.util.Objects;
import java.util.Optional;

import br.com.betufu.sports.EnrollmentFingerprintPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link EnrollmentFingerprintPort} using verification module internals.
 */
@Component
@Profile("runtime | integrated")
public class SportsEnrollmentFingerprintAdapter implements EnrollmentFingerprintPort {

    private final MatriculaHmac matriculaHmac;

    public SportsEnrollmentFingerprintAdapter(MatriculaHmac matriculaHmac) {
        this.matriculaHmac = Objects.requireNonNull(matriculaHmac, "matriculaHmac must not be null");
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
}
