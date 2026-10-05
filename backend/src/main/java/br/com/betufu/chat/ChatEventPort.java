package br.com.betufu.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consumer-owned publication boundary. No sports persistence types cross it. */
public interface ChatEventPort {
  record PublishedEvent(UUID eventId, UUID competitionId, UUID editionId) {}

  record DefinitiveMilestone(
      UUID eventId, UUID sourceFactId, UUID sourceVersionId, String kind, Instant milestoneAt) {}

  Optional<PublishedEvent> findPublished(UUID eventId);

  /** Returns only a producer-confirmed outcome; drafts and partial corrections are excluded. */
  default Optional<DefinitiveMilestone> findDefinitiveMilestone(UUID eventId) {
    return Optional.empty();
  }

  /** Immutable confirmed history, including superseded outcomes, in producer version order. */
  default List<DefinitiveMilestone> findDefinitiveMilestones(UUID eventId) {
    return findDefinitiveMilestone(eventId).stream().toList();
  }
}
