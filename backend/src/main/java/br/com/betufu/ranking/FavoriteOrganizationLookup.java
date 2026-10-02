package br.com.betufu.ranking;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned profile boundary for achievement evaluation. */
public interface FavoriteOrganizationLookup {
  Optional<UUID> findFavoriteOrganization(UUID identityId);
}
