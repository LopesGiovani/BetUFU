package br.com.betufu.ranking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model for a person's earned and revoked achievements. */
public interface AchievementQueryPort {
  enum AwardState {
    AWARDED,
    REVOKED
  }

  record Achievement(
      UUID awardId,
      String code,
      String name,
      String description,
      String rule,
      String catalogEdition,
      UUID sportsEditionId,
      AwardState state,
      Instant awardedAt,
      Instant revokedAt,
      String revocationReason) {}

  List<Achievement> findByIdentity(UUID identityId);
}
