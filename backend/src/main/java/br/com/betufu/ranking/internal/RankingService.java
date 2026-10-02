package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.RankingEnrollmentSource;
import br.com.betufu.ranking.RankingModels;
import br.com.betufu.ranking.RankingNicknameSource;
import br.com.betufu.ranking.RankingQueryPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("runtime | integrated")
public class RankingService implements RankingQueryPort {
  private final RankingRebuildService rebuild;
  private final RankingRepository repository;
  private final RankingEnrollmentSource verification;
  private final RankingNicknameSource nicknames;

  public RankingService(
      RankingRebuildService rebuild,
      RankingRepository repository,
      RankingEnrollmentSource verification,
      RankingNicknameSource nicknames) {
    this.rebuild = Objects.requireNonNull(rebuild);
    this.repository = Objects.requireNonNull(repository);
    this.verification = Objects.requireNonNull(verification);
    this.nicknames = Objects.requireNonNull(nicknames);
  }

  @Override
  public List<RankingModels.Entry> findLifetimeRanking() {
    List<PositionedCounts> ranked = rankedRows();
    List<RankingModels.Entry> entries = new ArrayList<>(ranked.size());
    for (PositionedCounts rankedRow : ranked) {
      RankingRepository.Counts row = rankedRow.counts();
      entries.add(
          new RankingModels.Entry(
              rankedRow.position(),
              nicknames.findNickname(row.identityId()).orElse("Participante"),
              row.wins(),
              row.losses(),
              rate(row),
              verification.isActiveNow(row.identityId())));
    }
    return List.copyOf(entries);
  }

  @Override
  public Optional<RankingModels.MemberMetrics> findMemberMetrics(UUID identityId) {
    Objects.requireNonNull(identityId);
    for (PositionedCounts rankedRow : rankedRows()) {
      RankingRepository.Counts row = rankedRow.counts();
      if (row.identityId().equals(identityId)) {
        return Optional.of(
            new RankingModels.MemberMetrics(
                rankedRow.position(),
                row.wins(),
                row.losses(),
                rate(row),
                verification.isActiveNow(identityId)));
      }
    }
    return Optional.empty();
  }

  private List<PositionedCounts> rankedRows() {
    rebuild.rebuild();
    List<RankingRepository.Counts> counts = new ArrayList<>(repository.findAll());
    counts.sort(
        Comparator.comparingLong(RankingRepository.Counts::wins)
            .reversed()
            .thenComparing(RankingService::compareRates)
            .thenComparing(RankingRepository.Counts::identityId));
    List<PositionedCounts> ranked = new ArrayList<>(counts.size());
    int position = 0;
    RankingRepository.Counts previous = null;
    for (int index = 0; index < counts.size(); index++) {
      RankingRepository.Counts row = counts.get(index);
      if (previous == null || row.wins() != previous.wins() || compareRates(row, previous) != 0) {
        position = index + 1;
      }
      ranked.add(new PositionedCounts(position, row));
      previous = row;
    }
    return ranked;
  }

  private static int compareRates(RankingRepository.Counts left, RankingRepository.Counts right) {
    return Long.compare(
        right.wins() * (left.wins() + left.losses()),
        left.wins() * (right.wins() + right.losses()));
  }

  private static BigDecimal rate(RankingRepository.Counts row) {
    return BigDecimal.valueOf(row.wins())
        .divide(BigDecimal.valueOf(row.wins() + row.losses()), 6, RoundingMode.HALF_UP);
  }

  private record PositionedCounts(int position, RankingRepository.Counts counts) {}
}
