package br.com.betufu.odds.internal.publicview;

import br.com.betufu.sports.PublicEventMarketLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SportsPublicEventMarketLookupAdapter implements PublicEventMarketLookup {

  private final OddsPublicViewRepository repository;

  public SportsPublicEventMarketLookupAdapter(OddsPublicViewRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<UUID> findPublicMarketId(UUID eventId) {
    return repository.findPublicMarketIdByEvent(eventId);
  }
}
