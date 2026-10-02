package br.com.betufu.ranking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One calculation and ordering contract for the lifetime ranking and future groups. */
public interface RankingQueryPort {
  List<RankingModels.Entry> findLifetimeRanking();

  Optional<RankingModels.MemberMetrics> findMemberMetrics(UUID identityId);
}
