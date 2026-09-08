package br.com.betufu.odds.internal.model;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure Elo replay with the owner-ratified initial rating, K threshold and edition regression. */
public final class EloRatingEngine {

  public static final BigDecimal INITIAL_RATING = new BigDecimal("1500");
  public static final BigDecimal PROVISIONAL_K = new BigDecimal("32");
  public static final BigDecimal MATURE_K = new BigDecimal("16");
  public static final int EXPERIENCE_THRESHOLD = 30;
  public static final BigDecimal EDITION_REGRESSION = new BigDecimal("0.5");

  public RatingSnapshot rate(
      List<ConfirmedSportsResultForOdds> results, UUID homeTeamId, UUID awayTeamId) {
    Map<UUID, BigDecimal> ratings = new HashMap<>();
    Map<UUID, Integer> games = new HashMap<>();
    Map<UUID, UUID> editions = new HashMap<>();
    Set<UUID> seenResults = new HashSet<>();

    List<ConfirmedSportsResultForOdds> ordered =
        new ArrayList<>(results == null ? List.of() : results);
    ordered.sort(
        Comparator.comparing(ConfirmedSportsResultForOdds::sportTime)
            .thenComparing(ConfirmedSportsResultForOdds::knownAt)
            .thenComparing(ConfirmedSportsResultForOdds::versionNo)
            .thenComparing(ConfirmedSportsResultForOdds::versionId));

    for (ConfirmedSportsResultForOdds result : ordered) {
      if (result.resultKind() != SportsResultKindForOdds.SPORTING_RESULT
          || !seenResults.add(result.versionId())
          || result.scores().size() != 2) {
        continue;
      }
      Score first = result.scores().get(0);
      Score second = result.scores().get(1);
      UUID firstTeam = first.teamId();
      UUID secondTeam = second.teamId();
      prepareTeam(firstTeam, result.editionId(), ratings, games, editions);
      prepareTeam(secondTeam, result.editionId(), ratings, games, editions);

      BigDecimal firstRating = ratings.get(firstTeam);
      BigDecimal secondRating = ratings.get(secondTeam);
      double expectedFirst = expected(firstRating.doubleValue(), secondRating.doubleValue());
      double actualFirst = Integer.compare(first.score(), second.score()) > 0 ? 1.0 : 0.0;
      if (first.score() == second.score()) {
        actualFirst = 0.5;
      }
      BigDecimal firstK = kFactor(games.get(firstTeam));
      BigDecimal secondK = kFactor(games.get(secondTeam));
      ratings.put(
          firstTeam,
          firstRating.add(firstK.multiply(BigDecimal.valueOf(actualFirst - expectedFirst))));
      ratings.put(
          secondTeam,
          secondRating.add(
              secondK.multiply(BigDecimal.valueOf((1.0 - actualFirst) - (1.0 - expectedFirst)))));
      games.merge(firstTeam, 1, Integer::sum);
      games.merge(secondTeam, 1, Integer::sum);
    }

    return new RatingSnapshot(
        ratings.getOrDefault(homeTeamId, INITIAL_RATING),
        ratings.getOrDefault(awayTeamId, INITIAL_RATING),
        games.getOrDefault(homeTeamId, 0),
        games.getOrDefault(awayTeamId, 0));
  }

  public BigDecimal kFactor(int gamesPlayed) {
    return gamesPlayed < EXPERIENCE_THRESHOLD ? PROVISIONAL_K : MATURE_K;
  }

  public BigDecimal regressForEdition(BigDecimal previousRating) {
    return INITIAL_RATING.add(
        previousRating
            .subtract(INITIAL_RATING)
            .multiply(BigDecimal.ONE.subtract(EDITION_REGRESSION)));
  }

  public double expected(double homeRating, double awayRating) {
    return 1.0 / (1.0 + Math.pow(10.0, (awayRating - homeRating) / 400.0));
  }

  private static void prepareTeam(
      UUID teamId,
      UUID editionId,
      Map<UUID, BigDecimal> ratings,
      Map<UUID, Integer> games,
      Map<UUID, UUID> editions) {
    BigDecimal previous = ratings.get(teamId);
    if (previous == null) {
      ratings.put(teamId, INITIAL_RATING);
      games.put(teamId, 0);
    } else if (!editionId.equals(editions.get(teamId))) {
      ratings.put(
          teamId,
          INITIAL_RATING.add(previous.subtract(INITIAL_RATING).multiply(new BigDecimal("0.5"))));
      games.put(teamId, 0);
    }
    editions.put(teamId, editionId);
  }

  public record RatingSnapshot(
      BigDecimal homeRating, BigDecimal awayRating, int homeGames, int awayGames) {
    public int conservativeSampleSize() {
      return Math.min(homeGames, awayGames);
    }

    public BigDecimal homeRatingRounded() {
      return homeRating.setScale(6, RoundingMode.HALF_UP);
    }
  }
}
