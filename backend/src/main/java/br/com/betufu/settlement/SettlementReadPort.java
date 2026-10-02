package br.com.betufu.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only settlement view; a running or failed case is never reported as complete. */
public interface SettlementReadPort {
  enum ProcessingStatus {
    PENDING,
    RUNNING,
    FAILED,
    COMPLETE
  }

  record CaseProgress(UUID caseId, ProcessingStatus status, int totalUnits, int completedUnits) {}

  record PredictionOutcome(
      UUID settlementId,
      UUID predictionId,
      SettlementState state,
      String reason,
      String ruleVersion,
      String selection,
      BigDecimal stake,
      BigDecimal acceptedOdds,
      BigDecimal potentialReturn,
      BigDecimal amount,
      String outcomeKind,
      Integer homeScore,
      Integer awayScore,
      Integer winnerSlotNo,
      UUID ledgerEntryId) {}

  /** Current complete settlement facts; a newer incomplete correction suppresses older facts. */
  record CurrentFact(
      UUID settlementId,
      UUID predictionId,
      UUID identityId,
      UUID eventId,
      UUID marketId,
      Instant acceptedAt,
      SettlementState state,
      String selection,
      BigDecimal acceptedOdds) {}

  Optional<CaseProgress> findCase(UUID caseId);

  Optional<PredictionOutcome> findLatestByPrediction(UUID predictionId);

  List<CurrentFact> findCurrentFacts();
}
