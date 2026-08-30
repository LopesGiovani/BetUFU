package br.com.betufu.sports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public sports-module boundary port for computing and resolving enrollment fingerprints.
 *
 * <p>Implemented by verification module adapter to ensure byte-compatibility with Phase 2
 * without sports depending on verification internal implementation details (D-69, D-70, D-71).
 */
public interface EnrollmentFingerprintPort {

    /**
     * Minimal event contract consumed by sports without importing verification internals.
     */
    interface EnrollmentVerifiedEvent {
        UUID identityId();
    }

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

    /**
     * Finds the active enrollment fingerprint for the given identityId.
     *
     * @param identityId opaque identity UUID
     * @return Fingerprint with 32-byte hash and version, or empty if identity has no active enrollment
     */
    Optional<Fingerprint> findActiveFingerprintByIdentityId(UUID identityId);

    /**
     * Finds active identity IDs matching the given 32-byte fingerprint hash.
     *
     * @param hash 32-byte fingerprint hash
     * @return list of active identity IDs matching the fingerprint
     */
    List<UUID> findActiveIdentitiesByFingerprint(byte[] hash);
}
