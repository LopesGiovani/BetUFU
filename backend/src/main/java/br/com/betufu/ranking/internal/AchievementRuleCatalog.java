package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.AchievementSettlementSource.CurrentFact;
import br.com.betufu.ranking.AchievementSettlementSource.Decision;
import br.com.betufu.ranking.SportsEventOrganizationLookup.EventOrganizations;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Closed v1 rules. Each candidate has a stable origin key for retry safety. */
public final class AchievementRuleCatalog {
  public static final String EDITION = "v1";

  private AchievementRuleCatalog() {}

  public record Candidate(
      UUID identityId, String code, String originKey, UUID originSettlementId, UUID editionId) {}

  public static List<Candidate> evaluate(
      UUID identityId,
      List<CurrentFact> facts,
      Map<UUID, EventOrganizations> events,
      Optional<UUID> favoriteOrganization,
      boolean hasThreeLifetimeWins) {
    List<Candidate> candidates = new ArrayList<>();
    Map<UUID, Map<UUID, CurrentFact>> roundEvents = new HashMap<>();
    Deque<CurrentFact> streak = new ArrayDeque<>();
    for (CurrentFact fact : facts) {
      EventOrganizations event = events.get(fact.eventId());
      if (fact.decision() == Decision.LOST) {
        streak.clear();
        continue;
      }
      if (fact.decision() != Decision.WON || event == null) continue;
      streak.addLast(fact);
      if (streak.size() > 3) streak.removeFirst();
      if (hasThreeLifetimeWins && streak.size() == 3) {
        candidates.add(candidate(identityId, "STREAK_3", lineage(streak), fact, event));
      }
      if (fact.acceptedOdds().compareTo(new BigDecimal("3.00")) >= 0) {
        candidates.add(candidate(identityId, "UNDERDOG_3", lineage(List.of(fact)), fact, event));
      }
      if (event.phaseId() != null) {
        Map<UUID, CurrentFact> round =
            roundEvents.computeIfAbsent(event.phaseId(), ignored -> new LinkedHashMap<>());
        round.putIfAbsent(fact.eventId(), fact);
        if (round.size() == 3 && hasThreeLifetimeWins) {
          candidates.add(candidate(identityId, "ROUND_3", lineage(round.values()), fact, event));
        }
      }
      if (favoriteOrganization.isPresent()
          && favoriteOrganization.get().equals(event.organizationFor(fact.selection()))) {
        candidates.add(candidate(identityId, "FAVORITE", lineage(List.of(fact)), fact, event));
      }
    }
    return List.copyOf(candidates);
  }

  private static Candidate candidate(
      UUID identityId, String code, String origin, CurrentFact fact, EventOrganizations event) {
    return new Candidate(identityId, code, origin, fact.settlementId(), event.editionId());
  }

  private static String lineage(Iterable<CurrentFact> facts) {
    List<String> ids = new ArrayList<>();
    for (CurrentFact fact : facts) ids.add(fact.settlementId().toString());
    return String.join(":", ids);
  }
}
