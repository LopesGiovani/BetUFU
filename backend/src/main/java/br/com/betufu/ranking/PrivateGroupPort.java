package br.com.betufu.ranking;

import java.time.Instant;
import java.util.UUID;

/** Commands for private social groups. Callers must pass the authenticated OIDC principal. */
public interface PrivateGroupPort {
  UUID create(String issuer, String subject, String name);

  void rename(String issuer, String subject, UUID groupId, String name);

  Invitation issueInvitation(String issuer, String subject, UUID groupId, Instant expiresAt);

  UUID join(String issuer, String subject, String code);

  void changeRole(String issuer, String subject, UUID groupId, UUID membershipId, Role role);

  void remove(String issuer, String subject, UUID groupId, UUID membershipId);

  void leave(String issuer, String subject, UUID groupId);

  void close(String issuer, String subject, UUID groupId);

  enum Role {
    OWNER,
    ADMIN,
    MEMBER
  }

  record Invitation(String code, Instant expiresAt) {}
}
