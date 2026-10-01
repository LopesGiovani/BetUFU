package br.com.betufu.settlement;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Consumer-owned snapshot of a published, current sports outcome. */
public interface SportsResultForSettlementPort {
  Optional<ResultSnapshot> findCurrent(UUID eventId);

  enum OutcomeKind {
    SPORTING_RESULT,
    ADMINISTRATIVE_WALKOVER,
    ABANDONED_NO_VALID_RESULT
  }

  record ResultSnapshot(
      UUID eventId,
      UUID outcomeFactId,
      UUID confirmedVersionId,
      OutcomeKind kind,
      Integer homeScore,
      Integer awayScore,
      Integer winnerSlotNo,
      Instant confirmedAt,
      Instant knownAt) {}
}
