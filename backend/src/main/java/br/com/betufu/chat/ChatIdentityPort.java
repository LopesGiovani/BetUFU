package br.com.betufu.chat;

import java.util.Optional;
import java.util.UUID;

/** Only the existing public identity UUID and display nickname cross this boundary. */
public interface ChatIdentityPort {
  record Participant(UUID participantId, String nickname) {}

  Optional<Participant> findParticipant(UUID identityId);
}
