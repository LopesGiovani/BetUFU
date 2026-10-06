package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Proves the chat guard consumes the same operational pause projection as administrators. */
class ChatPauseIT extends ChatWriteFixture {
  @BeforeEach
  void eligible() {
    jdbc.update(
        "INSERT INTO ufu_enrollment(identity_id,matricula_hmac,hmac_version,status) VALUES (?,decode(repeat('ab',32),'hex'),1,'ATIVO')",
        author);
  }

  @Test
  void accountPauseBlocksOnlyNewMessagesAndResumeRestoresWriting() throws Exception {
    UUID pauseId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO emergency_pause_fact(pause_fact_id,target_kind,target_id,actor_identity_id,action,reason,client_command_id,recorded_at) VALUES (?, 'ACCOUNT', ?, ?, 'PAUSE', 'fixture', ?, CURRENT_TIMESTAMP)",
        pauseId,
        author,
        author,
        UUID.randomUUID());
    jdbc.update(
        "INSERT INTO emergency_pause_target(target_kind,target_id,paused,current_pause_id) VALUES ('ACCOUNT',?,true,?)",
        author,
        pauseId);
    assertThat(post(session("reader"), null, UUID.randomUUID(), "blocked").statusCode())
        .isEqualTo(403);

    jdbc.update(
        "INSERT INTO emergency_pause_fact(pause_fact_id,target_kind,target_id,actor_identity_id,action,reason,client_command_id,resumed_pause_id,recorded_at) VALUES (?, 'ACCOUNT', ?, ?, 'RESUME', 'fixture resume', ?, ?, CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        author,
        author,
        UUID.randomUUID(),
        pauseId);
    assertThat(post(session("reader"), null, UUID.randomUUID(), "resumed").statusCode())
        .isEqualTo(201);
  }

  @Test
  void eventAndChannelPauseDoNotHideReadAvailability() throws Exception {
    assertThat(request(session("reader"), null, UUID.randomUUID(), "{}", true, ORIGIN).statusCode())
        .isEqualTo(400);
  }
}
