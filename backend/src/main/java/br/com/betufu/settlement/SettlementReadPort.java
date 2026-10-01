package br.com.betufu.settlement;

import java.math.BigDecimal;
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

  Optional<CaseProgress> findCase(UUID caseId);

  Optional<PredictionOutcome> findLatestByPrediction(UUID predictionId);
}
