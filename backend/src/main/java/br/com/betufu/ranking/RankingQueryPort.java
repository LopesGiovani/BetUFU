package br.com.betufu.ranking;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One calculation and ordering contract for the lifetime ranking and future groups. */
public interface RankingQueryPort {
  List<RankingModels.Entry> findLifetimeRanking();

  Optional<RankingModels.MemberMetrics> findMemberMetrics(UUID identityId);

  /** Reuses the lifetime ordering for a member-only subset; never expose IDs in an HTTP roster. */
  List<ScopedMember> rankMembers(Set<UUID> identityIds);

  record ScopedMember(UUID identityId, String nickname, RankingModels.MemberMetrics metrics) {}
}
