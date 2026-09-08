package br.com.betufu.sports.internal.odds;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SportsResultForOddsProducer implements SportsResultForOddsPort {

  private final SportsOddsSourceRepository repository;
  private final SportsOutcomeForOddsMapper mapper;

  SportsResultForOddsProducer(SportsOddsSourceRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.mapper = new SportsOutcomeForOddsMapper();
  }

  @Override
  public Optional<SportsEventForOdds> getEventForOdds(UUID eventId) {
    return repository.findEvent(eventId).map(mapper::mapEvent);
  }

  @Override
  public Optional<ConfirmedSportsResultForOdds> getConfirmedResultForOdds(
      UUID eventId, UUID confirmedVersionId) {
    return repository
        .findCurrentSportingOutcome(eventId, confirmedVersionId)
        .map(mapper::mapOutcome);
  }

  @Override
  public List<ConfirmedSportsResultForOdds> findAdmissibleResultsAsOf(
      UUID competitionId, UUID editionId, Instant asOfCutoff) {
    List<SportsOddsSourceRepository.OutcomeRow> rows =
        repository.findOutcomeFactsAsOf(competitionId, editionId, asOfCutoff);
    Map<UUID, SportsOddsSourceRepository.OutcomeRow> byFact = new HashMap<>();
    Map<UUID, Set<UUID>> successors = new HashMap<>();
    for (SportsOddsSourceRepository.OutcomeRow row : rows) {
      if (row.knownAt() == null || row.sportTime() == null || row.knownAt().isAfter(asOfCutoff)) {
        throw new IllegalStateException("outcome replay contains an invalid temporal fact");
      }
      if (byFact.put(row.factId(), row) != null) {
        throw new IllegalStateException("outcome replay contains duplicate fact ids");
      }
      if (row.supersedesFactId() != null) {
        successors
            .computeIfAbsent(row.supersedesFactId(), ignored -> new HashSet<>())
            .add(row.factId());
      }
    }
    for (SportsOddsSourceRepository.OutcomeRow row : rows) {
      if (row.supersedesFactId() != null) {
        SportsOddsSourceRepository.OutcomeRow predecessor = byFact.get(row.supersedesFactId());
        if (predecessor != null && !predecessor.eventId().equals(row.eventId())) {
          throw new IllegalStateException("outcome supersession crosses event boundaries");
        }
      }
    }
    validateReplayChains(byFact, successors);
    return byFact.values().stream()
        .filter(row -> !successors.containsKey(row.factId()))
        .sorted(
            java.util.Comparator.comparing(SportsOddsSourceRepository.OutcomeRow::sportTime)
                .thenComparing(SportsOddsSourceRepository.OutcomeRow::knownAt)
                .thenComparing(SportsOddsSourceRepository.OutcomeRow::factId))
        .filter(
            row ->
                row.outcomeKind().equals("SPORTING_RESULT")
                    || row.outcomeKind().equals("ADMINISTRATIVE_WALKOVER")
                    || row.outcomeKind().equals("ABANDONED_NO_VALID_RESULT"))
        .map(mapper::mapOutcome)
        .toList();
  }

  private static void validateReplayChains(
      Map<UUID, SportsOddsSourceRepository.OutcomeRow> byFact, Map<UUID, Set<UUID>> successors) {
    if (successors.values().stream().anyMatch(children -> children.size() > 1)) {
      throw new IllegalStateException("outcome replay contains a forked supersession chain");
    }
    for (UUID factId : byFact.keySet()) {
      Set<UUID> visited = new HashSet<>();
      UUID cursor = factId;
      while (cursor != null && byFact.containsKey(cursor)) {
        if (!visited.add(cursor)) {
          throw new IllegalStateException("outcome replay contains a supersession cycle");
        }
        cursor = byFact.get(cursor).supersedesFactId();
      }
    }
  }
}
