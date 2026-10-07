package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatRulesPreferencePort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatRulesPreferenceService implements ChatRulesPreferencePort {
  private final ChatRulesPreferenceRepository repository;
  private final ChatCommunityModels.Access access;

  public ChatRulesPreferenceService(
      ChatRulesPreferenceRepository repository, ChatCommunityModels.Access access) {
    this.repository = repository;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public boolean read(String issuer, String subject, String sessionId) {
    UUID owner = access.actor(issuer, subject, sessionId);
    return dismissed(owner);
  }

  @Transactional
  public boolean dismissForSession(String issuer, String subject, String sessionId) {
    UUID owner = access.actor(issuer, subject, sessionId);
    dismiss(owner);
    return true;
  }

  @Override
  public boolean dismissed(UUID ownerIdentityId) {
    if (ownerIdentityId == null) throw new IllegalArgumentException("owner identity required");
    return repository.dismissed(ownerIdentityId);
  }

  @Override
  public void dismiss(UUID ownerIdentityId) {
    if (ownerIdentityId == null) throw new IllegalArgumentException("owner identity required");
    repository.dismiss(ownerIdentityId);
  }
}
