package br.com.betufu.chat;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned publication boundary. No sports persistence types cross it. */
public interface ChatEventPort {
  record PublishedEvent(UUID eventId, UUID competitionId, UUID editionId) {}

  Optional<PublishedEvent> findPublished(UUID eventId);
}
