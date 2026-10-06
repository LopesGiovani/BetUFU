package br.com.betufu.chat;

import java.util.UUID;

/** Consumer-owned pause boundary used by chat commands and availability reads. */
public interface ChatEmergencyPausePort {
  enum TargetKind {
    ACCOUNT,
    EVENT,
    CHANNEL
  }

  record State(boolean paused, UUID revision) {}

  State lock(TargetKind kind, UUID targetId);

  State current(TargetKind kind, UUID targetId);
}
