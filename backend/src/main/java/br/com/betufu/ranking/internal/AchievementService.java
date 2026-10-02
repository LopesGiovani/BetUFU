package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.AchievementQueryPort;
import br.com.betufu.ranking.AchievementSettlementSource;
import br.com.betufu.ranking.AchievementSettlementSource.CurrentFact;
import br.com.betufu.ranking.FavoriteOrganizationLookup;
import br.com.betufu.ranking.RankingEnrollmentSource;
import br.com.betufu.ranking.RankingQueryPort;
import br.com.betufu.ranking.SportsEventOrganizationLookup;
import br.com.betufu.ranking.SportsEventOrganizationLookup.EventOrganizations;
import br.com.betufu.ranking.internal.AchievementRuleCatalog.Candidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class AchievementService implements AchievementQueryPort {
  private static final String CORRECTED_FACT =
      "O resultado atual nao satisfaz mais a regra desta conquista.";

  private final AchievementSettlementSource settlements;
  private final RankingQueryPort ranking;
  private final RankingEnrollmentSource eligibility;
  private final FavoriteOrganizationLookup favorites;
  private final SportsEventOrganizationLookup sports;
  private final AchievementRepository repository;

  public AchievementService(
      AchievementSettlementSource settlements,
      RankingQueryPort ranking,
      RankingEnrollmentSource eligibility,
      FavoriteOrganizationLookup favorites,
      SportsEventOrganizationLookup sports,
      AchievementRepository repository) {
    this.settlements = Objects.requireNonNull(settlements);
    this.ranking = Objects.requireNonNull(ranking);
    this.eligibility = Objects.requireNonNull(eligibility);
    this.favorites = Objects.requireNonNull(favorites);
    this.sports = Objects.requireNonNull(sports);
    this.repository = Objects.requireNonNull(repository);
  }

  @Override
  @Transactional
  public List<Achievement> findByIdentity(UUID identityId) {
    Objects.requireNonNull(identityId);
    reconcile();
    return repository.findByIdentity(identityId);
  }

  @Transactional
  public void reconcile() {
    Map<UUID, List<CurrentFact>> byIdentity = new HashMap<>();
    Map<UUID, EventOrganizations> events = new HashMap<>();
    Set<UUID> currentWinningSettlements = new HashSet<>();
    for (CurrentFact fact : settlements.findCurrentFacts()) {
      if (!eligibility.wasActiveAt(fact.identityId(), fact.acceptedAt())) continue;
      if (fact.decision() == AchievementSettlementSource.Decision.WON) {
        currentWinningSettlements.add(fact.settlementId());
      }
      byIdentity.computeIfAbsent(fact.identityId(), ignored -> new ArrayList<>()).add(fact);
      if (!events.containsKey(fact.eventId())) {
        sports
            .findEventOrganizations(fact.eventId())
            .ifPresent(context -> events.put(fact.eventId(), context));
      }
    }

    Set<AwardKey> valid = new HashSet<>();
    Set<UUID> peopleWithThreeWins = new HashSet<>();
    for (RankingQueryPort.ScopedMember member : ranking.rankMembers(byIdentity.keySet())) {
      if (member.metrics().wins() >= 3) peopleWithThreeWins.add(member.identityId());
    }
    for (Map.Entry<UUID, List<CurrentFact>> entry : byIdentity.entrySet()) {
      UUID person = entry.getKey();
      List<CurrentFact> facts = entry.getValue();
      facts.sort(
          Comparator.comparing(CurrentFact::acceptedAt).thenComparing(CurrentFact::predictionId));
      for (Candidate candidate :
          AchievementRuleCatalog.evaluate(
              person,
              facts,
              events,
              favorites.findFavoriteOrganization(person),
              peopleWithThreeWins.contains(person))) {
        repository.grant(candidate);
        valid.add(new AwardKey(person, candidate.code(), candidate.originKey()));
      }
    }
    for (AchievementRepository.ActiveAward award : repository.findActiveAwards()) {
      boolean favoriteStillWon =
          "FAVORITE".equals(award.code())
              && currentWinningSettlements.contains(award.originSettlementId());
      if (!favoriteStillWon
          && !valid.contains(new AwardKey(award.identityId(), award.code(), award.originKey()))) {
        repository.revoke(award.awardId(), CORRECTED_FACT);
      }
    }
  }

  private record AwardKey(UUID identityId, String code, String originKey) {}
}
