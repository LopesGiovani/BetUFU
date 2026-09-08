package br.com.betufu.odds.internal.model;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic filter for odds calculation history.
 *
 * <p>Enforces two-time temporal cutoff (knownAt and sportTime), resolves supersession chains
 * without mutating history, and excludes administrative walkovers, abandonments, and lifecycle
 * facts from feeding the Elo rating engine.
 */
public final class OddsInputFilter {

  private static final Comparator<ConfirmedSportsResultForOdds> CANONICAL_ORDER =
      Comparator.comparing(ConfirmedSportsResultForOdds::sportTime)
          .thenComparing(ConfirmedSportsResultForOdds::knownAt)
          .thenComparing(ConfirmedSportsResultForOdds::versionNo)
          .thenComparing(ConfirmedSportsResultForOdds::versionId);

  /** Filters raw confirmed results up to an as-of cutoff. */
  public List<ConfirmedSportsResultForOdds> filterAdmissibleResults(
      List<ConfirmedSportsResultForOdds> rawResults, Instant asOfCutoff) {
    return filterAdmissibleResults(rawResults, asOfCutoff, null);
  }

  /**
   * Filters raw confirmed results up to an as-of cutoff and strictly before target event scheduled
   * time.
   */
  public List<ConfirmedSportsResultForOdds> filterAdmissibleResults(
      List<ConfirmedSportsResultForOdds> rawResults, Instant asOfCutoff, Instant scheduledAt) {
    Objects.requireNonNull(rawResults, "rawResults must not be null");
    Objects.requireNonNull(asOfCutoff, "asOfCutoff must not be null");

    Map<UUID, ConfirmedSportsResultForOdds> byVersion = new HashMap<>();
    Map<UUID, Set<UUID>> successors = new HashMap<>();

    for (ConfirmedSportsResultForOdds result : rawResults) {
      Objects.requireNonNull(result, "result element must not be null");
      if (result.knownAt() == null || result.sportTime() == null) {
        throw new IllegalStateException("result fact must have non-null knownAt and sportTime");
      }

      // Facts known after the as-of cutoff are ignored as of this historical point.
      if (result.knownAt().isAfter(asOfCutoff)) {
        continue;
      }

      // Facts occurring at or after target scheduled time are excluded from pre-game history.
      if (scheduledAt != null && !result.sportTime().isBefore(scheduledAt)) {
        continue;
      }

      if (byVersion.put(result.versionId(), result) != null) {
        throw new IllegalStateException(
            "duplicate versionId in replay facts: " + result.versionId());
      }

      if (result.supersedesVersionId() != null) {
        successors
            .computeIfAbsent(result.supersedesVersionId(), ignored -> new HashSet<>())
            .add(result.versionId());
      }
    }

    validateSupersessionChains(byVersion, successors);

    return byVersion.values().stream()
        .filter(result -> !successors.containsKey(result.versionId()))
        .filter(OddsInputFilter::isAdmissibleSportingResult)
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  /**
   * Checks if an individual active fact qualifies as an admissible sporting result that feeds the
   * rating model.
   */
  public static boolean isAdmissibleSportingResult(ConfirmedSportsResultForOdds result) {
    if (result == null || result.resultKind() == null) {
      return false;
    }
    if (!result.resultKind().feedsRating()) {
      return false;
    }
    if (result.resultKind() != SportsResultKindForOdds.SPORTING_RESULT) {
      return false;
    }
    if (!result.resultKind().hasSportsScore()) {
      return false;
    }
    List<ConfirmedSportsResultForOdds.Score> scores = result.scores();
    if (scores == null || scores.size() != 2) {
      return false;
    }
    if (scores.get(0).slotNo() != 1 || scores.get(1).slotNo() != 2) {
      return false;
    }
    return true;
  }

  private static void validateSupersessionChains(
      Map<UUID, ConfirmedSportsResultForOdds> byVersion, Map<UUID, Set<UUID>> successors) {
    for (ConfirmedSportsResultForOdds result : byVersion.values()) {
      if (result.supersedesVersionId() != null) {
        ConfirmedSportsResultForOdds predecessor = byVersion.get(result.supersedesVersionId());
        if (predecessor != null && !predecessor.eventId().equals(result.eventId())) {
          throw new IllegalStateException("outcome supersession crosses event boundaries");
        }
      }
    }

    if (successors.values().stream().anyMatch(children -> children.size() > 1)) {
      throw new IllegalStateException("outcome replay contains a forked supersession chain");
    }

    for (UUID versionId : byVersion.keySet()) {
      Set<UUID> visited = new HashSet<>();
      UUID cursor = versionId;
      while (cursor != null && byVersion.containsKey(cursor)) {
        if (!visited.add(cursor)) {
          throw new IllegalStateException("outcome replay contains a supersession cycle");
        }
        cursor = byVersion.get(cursor).supersedesVersionId();
      }
    }
  }
}
