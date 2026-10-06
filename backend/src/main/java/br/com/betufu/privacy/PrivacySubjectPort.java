package br.com.betufu.privacy;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned projections used by the privacy module. */
public interface PrivacySubjectPort {
  interface Identity {
    Optional<UUID> findAuthenticatedIdentity(String issuer, String subject);

    boolean isGeneralAdministrator(UUID identityId);
  }

  interface Sports {
    boolean existsAthlete(UUID athleteId);
  }
}
