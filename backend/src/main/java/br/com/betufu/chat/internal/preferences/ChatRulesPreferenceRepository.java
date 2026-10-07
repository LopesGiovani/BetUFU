package br.com.betufu.chat.internal;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatRulesPreferenceRepository {
  private final JdbcTemplate jdbc;

  public ChatRulesPreferenceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean dismissed(UUID ownerIdentityId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chat_rules_dismissal WHERE owner_identity_id=?)",
            Boolean.class,
            ownerIdentityId));
  }

  public void dismiss(UUID ownerIdentityId) {
    jdbc.update(
        "INSERT INTO chat_rules_dismissal(owner_identity_id) VALUES (?) ON CONFLICT (owner_identity_id) DO NOTHING",
        ownerIdentityId);
  }
}
