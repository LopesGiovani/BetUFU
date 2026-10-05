package br.com.betufu.chat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Consumer-owned authority boundary for scoped chat moderation. */
public interface ChatModerationAuthorityPort {
  record Authority(UUID identityId, boolean generalAdministrator, Set<UUID> moderatorScopes) {
    public Authority {
      if (identityId == null) throw new IllegalArgumentException("identityId must not be null");
      moderatorScopes = moderatorScopes == null ? Set.of() : Set.copyOf(moderatorScopes);
    }

    public boolean canModerate(UUID competitionId) {
      return generalAdministrator
          || (competitionId != null && moderatorScopes.contains(competitionId));
    }
  }

  Optional<Authority> resolve(String issuer, String subject);
}
