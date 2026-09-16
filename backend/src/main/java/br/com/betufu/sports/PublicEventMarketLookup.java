package br.com.betufu.sports;

import java.util.Optional;
import java.util.UUID;

/** Public sports boundary for discovering a market already available to anonymous users. */
public interface PublicEventMarketLookup {

  Optional<UUID> findPublicMarketId(UUID eventId);
}
