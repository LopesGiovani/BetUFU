package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatCommunityModels.ReportReason;
import br.com.betufu.chat.internal.ChatModerationModels;
import br.com.betufu.chat.internal.ChatModerationService;
import br.com.betufu.chat.internal.ChatReportService;
import br.com.betufu.platform.api.ApiProblemException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatModerationIT extends ChatCommunityFixture {
  @Autowired ChatReportService reports;
  @Autowired ChatModerationService moderation;
  @Autowired br.com.betufu.chat.internal.ChatReadService reads;

  @Test
  void scopedModeratorRemovesOnlyItsEventAndRetryKeepsOneImmutableFact() {
    UUID moderator = participant("moderator");
    UUID event = event(true);
    UUID channel = channel(event);
    UUID message = message(channel, "original moderation evidence");
    UUID competition =
        jdbc.queryForObject(
            "SELECT competition_id FROM sports_event WHERE event_id=?", UUID.class, event);
    grant(moderator, "MODERADOR", competition);
    var reporter = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reporter.getId(),
            message,
            UUID.randomUUID(),
            ReportReason.SPAM);
    var moderatorSession = session("moderator");

    var opened =
        moderation.open(
            "https://idp.invalid", "moderator", moderatorSession.getId(), report.reportId());
    var command = new ChatModerationModels.Command(UUID.randomUUID(), "violates the channel rule");
    var removed =
        moderation.remove(
            "https://idp.invalid", "moderator", moderatorSession.getId(), opened.caseId(), command);
    var replay =
        moderation.remove(
            "https://idp.invalid", "moderator", moderatorSession.getId(), opened.caseId(), command);

    assertThat(removed.status()).isEqualTo("RESOLVED");
    assertThat(replay).isEqualTo(removed);
    assertThat(
            jdbc.queryForObject(
                "SELECT body FROM chat_message WHERE message_id=?", String.class, message))
        .isEqualTo("original moderation evidence");
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM chat_message_projection WHERE message_id=?",
                String.class,
                message))
        .isEqualTo("REMOVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_message_removal_fact WHERE message_id=?",
                Integer.class,
                message))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_decision_fact WHERE case_id=?",
                Integer.class,
                opened.caseId()))
        .isEqualTo(1);
  }

  @Test
  void scopedModeratorIsDeniedOutsideScopeAndAfterRevocation() {
    UUID moderator = participant("scoped-moderator");
    UUID event = event(true);
    UUID channel = channel(event);
    UUID message = message(channel, "scoped report");
    UUID wrongScope = UUID.randomUUID();
    grant(moderator, "MODERADOR", wrongScope);
    var reporter = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reporter.getId(),
            message,
            UUID.randomUUID(),
            ReportReason.OFENSA);
    var moderatorSession = session("scoped-moderator");

    assertThatThrownBy(
            () ->
                moderation.open(
                    "https://idp.invalid",
                    "scoped-moderator",
                    moderatorSession.getId(),
                    report.reportId()))
        .isInstanceOf(ApiProblemException.class)
        .extracting(error -> ((ApiProblemException) error).status().value())
        .isEqualTo(403);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_moderation_case", Integer.class))
        .isZero();

    UUID actualScope =
        jdbc.queryForObject(
            "SELECT competition_id FROM sports_event WHERE event_id=?", UUID.class, event);
    jdbc.update(
        "UPDATE operational_role_grant SET scope_ref=? WHERE identity_id=?",
        actualScope,
        moderator);
    var opened =
        moderation.open(
            "https://idp.invalid", "scoped-moderator", moderatorSession.getId(), report.reportId());
    jdbc.update(
        "UPDATE operational_role_grant SET status='REVOGADO',revoked_at=CURRENT_TIMESTAMP WHERE identity_id=?",
        moderator);
    assertThatThrownBy(
            () ->
                moderation.list(
                    "https://idp.invalid", "scoped-moderator", moderatorSession.getId()))
        .isInstanceOf(ApiProblemException.class)
        .extracting(error -> ((ApiProblemException) error).status().value())
        .isEqualTo(403);
    assertThat(opened.caseId()).isNotNull();
  }

  @Test
  void forwardingIsScopedIdempotentAndDoesNotRemoveOrPunish() {
    UUID moderator = participant("forward-moderator");
    UUID event = event(true);
    UUID channel = channel(event);
    UUID message = message(channel, "evidence remains public until removal");
    UUID competition =
        jdbc.queryForObject(
            "SELECT competition_id FROM sports_event WHERE event_id=?", UUID.class, event);
    grant(moderator, "MODERADOR", competition);
    var reader = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reader.getId(),
            message,
            UUID.randomUUID(),
            ReportReason.SPAM);
    var actor = session("forward-moderator");
    var opened =
        moderation.open(
            "https://idp.invalid", "forward-moderator", actor.getId(), report.reportId());
    var command =
        new ChatModerationModels.Command(UUID.randomUUID(), "administrator review requested");
    var receipt =
        moderation.forward(
            "https://idp.invalid", "forward-moderator", actor.getId(), opened.caseId(), command);
    assertThat(
            moderation.forward(
                "https://idp.invalid",
                "forward-moderator",
                actor.getId(),
                opened.caseId(),
                command))
        .isEqualTo(receipt);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_forward_fact WHERE case_id=?",
                Integer.class,
                opened.caseId()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message_removal_fact", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT body FROM chat_message WHERE message_id=?", String.class, message))
        .isEqualTo("evidence remains public until removal");
    assertThatThrownBy(
            () ->
                moderation.forward(
                    "https://idp.invalid",
                    "forward-moderator",
                    actor.getId(),
                    opened.caseId(),
                    new ChatModerationModels.Command(command.clientCommandId(), "changed payload")))
        .isInstanceOf(ApiProblemException.class)
        .extracting(error -> ((ApiProblemException) error).status().value())
        .isEqualTo(409);
    jdbc.update(
        "UPDATE operational_role_grant SET status='REVOGADO',revoked_at=CURRENT_TIMESTAMP WHERE identity_id=?",
        moderator);
    assertThatThrownBy(
            () ->
                moderation.forward(
                    "https://idp.invalid",
                    "forward-moderator",
                    actor.getId(),
                    opened.caseId(),
                    command))
        .isInstanceOf(ApiProblemException.class)
        .extracting(error -> ((ApiProblemException) error).status().value())
        .isEqualTo(403);
  }

  @Test
  void removalOfAnOldMessageAdvancesTheReadCursorAndNeverLeaksOriginal() {
    UUID admin = participant("cursor-admin");
    grant(admin, "ADMINISTRADOR_GERAL", null);
    UUID original = message(global, "old secret original");
    for (int i = 0; i < 5; i++) message(global, "later visible message " + i);
    var before = reads.read(null, true, null, null, 100);
    var reader = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reader.getId(),
            original,
            UUID.randomUUID(),
            ReportReason.ASSEDIO);
    var actor = session("cursor-admin");
    var opened =
        moderation.open("https://idp.invalid", "cursor-admin", actor.getId(), report.reportId());
    moderation.remove(
        "https://idp.invalid",
        "cursor-admin",
        actor.getId(),
        opened.caseId(),
        new ChatModerationModels.Command(UUID.randomUUID(), "confirmed violation"));
    var delta = reads.read(null, true, null, before.changeCursor(), 100);
    assertThat(delta.messages()).hasSize(1);
    assertThat(delta.messages().getFirst().messageId()).isEqualTo(original);
    assertThat(delta.messages().getFirst().removed()).isTrue();
    assertThat(json.writeValueAsString(delta)).doesNotContain("old secret original");
    assertThat(delta.changeCursor()).isGreaterThan(before.changeCursor());
  }

  @Test
  void queueDiscoversNewReportsWithoutManualIdentifiersAndScopesTheResults() {
    UUID moderator = participant("queue-moderator");
    UUID event = event(true);
    UUID competition =
        jdbc.queryForObject(
            "SELECT competition_id FROM sports_event WHERE event_id=?", UUID.class, event);
    grant(moderator, "MODERADOR", competition);
    UUID ownMessage = message(channel(event), "reported in own scope");
    UUID otherMessage = message(channel(event(true)), "reported elsewhere");
    var reader = session("reader");
    reports.report(
        "https://idp.invalid",
        "reader",
        reader.getId(),
        ownMessage,
        UUID.randomUUID(),
        ReportReason.SPAM);
    reports.report(
        "https://idp.invalid",
        "reader",
        reader.getId(),
        otherMessage,
        UUID.randomUUID(),
        ReportReason.SPAM);
    var actor = session("queue-moderator");
    var queue = moderation.list("https://idp.invalid", "queue-moderator", actor.getId());
    assertThat(queue).hasSize(1);
    assertThat(queue.getFirst().messageId()).isEqualTo(ownMessage);
    assertThat(moderation.list("https://idp.invalid", "queue-moderator", actor.getId()))
        .isEqualTo(queue);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_moderation_case", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
  }

  private void grant(UUID identity, String role, UUID scope) {
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,scope_ref,status) VALUES (?,?,?,'VIGENTE')",
        identity,
        role,
        scope);
  }
}
