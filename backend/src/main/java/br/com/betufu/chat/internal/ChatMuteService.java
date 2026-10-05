package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatIdentityPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatMuteService {
  private final ChatMuteRepository repository;
  private final ChatCommunityModels.Access access;
  private final ChatIdentityPort identities;
  private final ChatPushService push;

  public ChatMuteService(
      ChatMuteRepository repository,
      ChatCommunityModels.Access access,
      ChatIdentityPort identities,
      ChatPushService push) {
    this.repository = repository;
    this.access = access;
    this.identities = identities;
    this.push = push;
  }

  @Transactional
  public ChatCommunityModels.MuteState read(String issuer, String subject, String sessionId) {
    UUID owner = access.actor(issuer, subject, sessionId);
    long version = repository.lockAccount(owner);
    access.requireCurrent(issuer, subject, sessionId);
    return repository.state(owner, version);
  }

  @Transactional
  public ChatCommunityModels.MuteState change(
      String issuer,
      String subject,
      String sessionId,
      UUID target,
      long expectedVersion,
      boolean muted) {
    UUID owner = access.actor(issuer, subject, sessionId);
    if (target == null || owner.equals(target) || expectedVersion < 0)
      throw ChatCommunityModels.invalid();
    long version = repository.lockAccount(owner);
    access.requireCurrent(issuer, subject, sessionId);
    if (version != expectedVersion) throw ChatCommunityModels.conflict();
    if (muted && identities.findParticipant(target).isEmpty()) throw ChatCommunityModels.missing();
    if (repository.change(owner, target, muted)) {
      version++;
      push.preferencesChanged(issuer, subject);
    }
    access.requireCurrent(issuer, subject, sessionId);
    return repository.state(owner, version);
  }
}
