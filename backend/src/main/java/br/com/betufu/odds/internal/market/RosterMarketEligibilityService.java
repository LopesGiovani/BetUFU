package br.com.betufu.odds.internal.market;

import br.com.betufu.sports.SportsRosterForOddsPort;
import br.com.betufu.sports.SportsRosterForOddsPort.SportsRosterBindingForOdds;
import br.com.betufu.sports.SportsRosterSnapshotForOdds;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Fails closed unless both event slots reference exact published roster snapshots. */
@Service
@Profile("runtime | integrated")
public class RosterMarketEligibilityService {

  private final SportsRosterForOddsPort rosters;

  public RosterMarketEligibilityService(SportsRosterForOddsPort rosters) {
    this.rosters = Objects.requireNonNull(rosters, "rosters must not be null");
  }

  public List<SportsRosterBindingForOdds> bindMarketVersion(
      UUID marketVersionId, UUID eventId, List<SportsRosterSnapshotForOdds> snapshots) {
    return rosters.bindMarketVersion(marketVersionId, eventId, snapshots);
  }

  public boolean isEligible(
      UUID marketVersionId, UUID eventId, List<SportsRosterBindingForOdds> bindings) {
    return bindings != null
        && bindings.size() == 2
        && bindings.stream()
            .allMatch(
                binding ->
                    marketVersionId.equals(binding.marketVersionId())
                        && eventId.equals(binding.eventId()));
  }
}
