package br.com.betufu.identity.internal;

import br.com.betufu.chat.ChatModerationAuthorityPort;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatModerationAuthorityAdapter implements ChatModerationAuthorityPort {
  private final OperationalIdentityLookup identities;

  public ChatModerationAuthorityAdapter(OperationalIdentityLookup identities) {
    this.identities = identities;
  }

  @Override
  public Optional<Authority> resolve(String issuer, String subject) {
    return identities
        .lookupIdentity(issuer, subject)
        .map(
            identity -> {
              boolean administrator = false;
              Set<UUID> scopes = new HashSet<>();
              for (var grant : identity.roles()) {
                if ("ADMINISTRADOR_GERAL".equals(grant.roleName()) && grant.scopeRef() == null) {
                  administrator = true;
                } else if ("MODERADOR".equals(grant.roleName()) && grant.scopeRef() != null) {
                  scopes.add(grant.scopeRef());
                }
              }
              return new Authority(identity.identityId(), administrator, scopes);
            });
  }
}
