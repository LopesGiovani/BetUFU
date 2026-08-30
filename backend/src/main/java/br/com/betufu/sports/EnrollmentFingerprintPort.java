package br.com.betufu.sports;

import java.util.Optional;

/**
 * Public sports-module boundary port for computing deterministic enrollment fingerprints.
 *
 * <p>Implemented by verification module adapter to ensure byte-compatibility with Phase 2
 * without sports depending on verification internal implementation details (D-69, D-70).
 */
public interface EnrollmentFingerprintPort {

    record Fingerprint(byte[] hash, short version) {
        public Fingerprint {
            if (hash == null || hash.length != 32) {
                throw new IllegalArgumentException("Fingerprint hash must be 32 bytes");
            }
            if (version < 1) {
                throw new IllegalArgumentException("Version must be positive");
            }
            hash = hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash == null ? null : hash.clone();
        }
    }

    /**
     * Normalizes and computes the current version fingerprint for the given raw matricula.
     *
     * @param rawMatricula raw matricula input (in-memory only)
     * @return Fingerprint with 32-byte hash and version, or empty if rawMatricula is null or blank
     * @throws IllegalArgumentException if rawMatricula is non-blank but invalid under approved pattern
     */
    Optional<Fingerprint> computeFingerprint(String rawMatricula);
}
