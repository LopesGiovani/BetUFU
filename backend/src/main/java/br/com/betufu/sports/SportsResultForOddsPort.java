package br.com.betufu.sports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public sports boundary consumed by odds; implementations remain inside sports. */
public interface SportsResultForOddsPort {

  Optional<SportsEventForOdds> getEventForOdds(UUID eventId);

  Optional<ConfirmedSportsResultForOdds> getConfirmedResultForOdds(
      UUID eventId, UUID confirmedVersionId);

  List<ConfirmedSportsResultForOdds> findAdmissibleResultsAsOf(
      UUID competitionId, UUID editionId, Instant asOfCutoff);
}
