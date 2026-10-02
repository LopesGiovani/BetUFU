package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.RankingEnrollmentSource;
import br.com.betufu.ranking.RankingSettlementSource;
import br.com.betufu.ranking.RankingSettlementSource.Decision;
import br.com.betufu.ranking.RankingSettlementSource.DecisivePrediction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class RankingRebuildService {
  private final RankingRepository repository;
  private final RankingSettlementSource settlements;
  private final RankingEnrollmentSource verification;
  private final TransactionTemplate transactions;

  public RankingRebuildService(
      RankingRepository repository,
      RankingSettlementSource settlements,
      RankingEnrollmentSource verification,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository);
    this.settlements = Objects.requireNonNull(settlements);
    this.verification = Objects.requireNonNull(verification);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  public void rebuild() {
    transactions.executeWithoutResult(
        ignored -> {
          repository.lockRebuild();
          Map<UUID, MutableCounts> totals = new HashMap<>();
          Set<IdentityMarket> seenMarkets = new HashSet<>();
          for (DecisivePrediction prediction : settlements.findCurrentDecisions()) {
            if (!verification.wasActiveAt(prediction.identityId(), prediction.acceptedAt())) {
              continue;
            }
            if (!seenMarkets.add(
                new IdentityMarket(prediction.identityId(), prediction.marketId()))) {
              throw new IllegalStateException(
                  "multiple decisive predictions for one identity and market");
            }
            MutableCounts counts =
                totals.computeIfAbsent(prediction.identityId(), unused -> new MutableCounts());
            if (prediction.decision() == Decision.WON) {
              counts.wins++;
            } else {
              counts.losses++;
            }
          }
          List<RankingRepository.Counts> rows = new ArrayList<>();
          totals.forEach(
              (identityId, counts) ->
                  rows.add(new RankingRepository.Counts(identityId, counts.wins, counts.losses)));
          repository.replaceAll(rows);
        });
  }

  private record IdentityMarket(UUID identityId, UUID marketId) {}

  private static final class MutableCounts {
    private long wins;
    private long losses;
  }
}
