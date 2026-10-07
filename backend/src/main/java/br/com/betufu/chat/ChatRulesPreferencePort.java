package br.com.betufu.chat;

import java.util.UUID;

/** Consumer-owned account preference used by the chat rules popup. */
public interface ChatRulesPreferencePort {
  boolean dismissed(UUID ownerIdentityId);

  void dismiss(UUID ownerIdentityId);
}
