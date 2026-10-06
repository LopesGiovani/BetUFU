package br.com.betufu.betting;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned operational pause boundary for prediction acceptance. */
public interface PredictionEmergencyPausePort {
  enum TargetKind {
    ACCOUNT,
    EVENT,
    MARKET
  }

  record State(boolean paused, UUID revision) {}

  record Market(UUID marketId, UUID eventId, boolean published) {}

  State lock(TargetKind kind, UUID targetId);

  State current(TargetKind kind, UUID targetId);

  Optional<Market> findMarket(UUID marketId);
}
