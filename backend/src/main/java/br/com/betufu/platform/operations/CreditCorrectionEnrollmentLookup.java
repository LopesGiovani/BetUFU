package br.com.betufu.platform.operations;

import java.util.UUID;

/** Checks enrollment ownership without returning the number, digest, or key version. */
public interface CreditCorrectionEnrollmentLookup {
  boolean belongsTo(UUID enrollmentId, UUID identityId);
}
