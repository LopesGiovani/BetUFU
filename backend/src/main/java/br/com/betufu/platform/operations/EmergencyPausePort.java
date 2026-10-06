package br.com.betufu.platform.operations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Operations owns both the neutral target lookup and the pause-state boundary. */
public interface EmergencyPausePort {
  enum Kind {
    ACCOUNT,
    EVENT,
    MARKET,
    CHANNEL
  }

  enum Action {
    PAUSE,
    RESUME
  }

  record Target(UUID relatedEventId) {}

  interface TargetLookup {
    Kind kind();

    Optional<Target> find(UUID targetId);
  }

  record Command(
      UUID clientCommandId,
      Kind kind,
      UUID targetId,
      Action action,
      String reason,
      UUID resumedPauseId) {}

  record Receipt(
      UUID factId,
      Kind kind,
      UUID targetId,
      Action action,
      String reason,
      UUID resumedPauseId,
      Instant recordedAt) {}

  record State(boolean paused, UUID currentPauseId) {}

  /** Caller transaction must retain the target lock until its command commits. */
  State lockState(Kind kind, UUID targetId);

  State state(Kind kind, UUID targetId);
}
