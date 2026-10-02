package br.com.betufu.ranking;

import java.time.Instant;
import java.util.UUID;

/** Verification answers only eligibility questions; enrollment details never cross this port. */
public interface RankingEnrollmentSource {
  boolean wasActiveAt(UUID identityId, Instant acceptedAt);

  boolean isActiveNow(UUID identityId);
}
