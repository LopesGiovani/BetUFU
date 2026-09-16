package br.com.betufu.identity;

import java.util.UUID;

/** Consumer-owned boundary for the enrollment status shown in the account profile. */
public interface ProfileEnrollmentStatusLookup {

  enum Status {
    REGISTRADA,
    NAO_REGISTRADA
  }

  Status findStatus(UUID identityId);
}
