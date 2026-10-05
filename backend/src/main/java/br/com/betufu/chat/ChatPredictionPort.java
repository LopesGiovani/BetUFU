package br.com.betufu.chat;

import java.util.UUID;

/** Consumer-owned view of prediction coverage for one event. */
public interface ChatPredictionPort {
  record EventPredictions(int marketCount, int acceptedCount) {
    public boolean hasAccepted() {
      return acceptedCount > 0;
    }
  }

  EventPredictions inspect(UUID eventId);
}
