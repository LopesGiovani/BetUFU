package br.com.betufu.chat.internal;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatMuteRepository {
  private final JdbcTemplate jdbc;

  public ChatMuteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long lockAccount(UUID owner) {
    jdbc.update(
        "INSERT INTO public.chat_mute_account(owner_identity_id) VALUES (?) ON CONFLICT DO NOTHING",
        owner);
    return jdbc.queryForObject(
        "SELECT version FROM public.chat_mute_account WHERE owner_identity_id=? FOR UPDATE",
        Long.class,
        owner);
  }

  ChatCommunityModels.MuteState state(UUID owner, long version) {
    return new ChatCommunityModels.MuteState(
        version,
        jdbc.queryForList(
            "SELECT participant_id FROM public.chat_mute WHERE owner_identity_id=? ORDER BY participant_id",
            UUID.class,
            owner));
  }

  boolean change(UUID owner, UUID target, boolean muted) {
    int changed =
        muted
            ? jdbc.update(
                "INSERT INTO public.chat_mute(owner_identity_id,participant_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                owner,
                target)
            : jdbc.update(
                "DELETE FROM public.chat_mute WHERE owner_identity_id=? AND participant_id=?",
                owner,
                target);
    if (changed > 0)
      jdbc.update(
          "UPDATE public.chat_mute_account SET version=version+1 WHERE owner_identity_id=?", owner);
    return changed > 0;
  }
}
