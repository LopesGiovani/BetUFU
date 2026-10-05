package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatCommunityModels.ReportReason;
import br.com.betufu.chat.internal.ChatModerationService;
import br.com.betufu.chat.internal.ChatReportService;
import br.com.betufu.platform.api.ApiProblemException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatEvidenceAccessIT extends ChatCommunityFixture {
  @Autowired ChatReportService reports;
  @Autowired ChatModerationService moderation;

  @Test
  void evidenceIsReturnedOnlyToAuthorizedModeratorAndEveryReadIsAudited() {
    UUID admin = participant("general-admin");
    grant(admin, "ADMINISTRADOR_GERAL", null);
    UUID message = message(global, "restricted original");
    var reporter = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reporter.getId(),
            message,
            UUID.randomUUID(),
            ReportReason.ASSEDIO);
    var adminSession = session("general-admin");
    var opened =
        moderation.open(
            "https://idp.invalid", "general-admin", adminSession.getId(), report.reportId());

    var evidence =
        moderation.evidence(
            "https://idp.invalid",
            "general-admin",
            adminSession.getId(),
            opened.caseId(),
            "review complaint");
    var again =
        moderation.evidence(
            "https://idp.invalid",
            "general-admin",
            adminSession.getId(),
            opened.caseId(),
            "second review");

    assertThat(evidence.originalBody()).isEqualTo("restricted original");
    assertThat(evidence.reports()).hasSize(1);
    assertThat(evidence.reports().getFirst().reason()).isEqualTo("ASSEDIO");
    assertThat(again.originalBody()).isEqualTo("restricted original");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_evidence_access_fact WHERE case_id=?",
                Integer.class,
                opened.caseId()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_message_removal_fact WHERE message_id=?",
                Integer.class,
                message))
        .isZero();
  }

  @Test
  void thirdPartyCannotReadOriginalEvidence() {
    UUID admin = participant("evidence-admin");
    grant(admin, "ADMINISTRADOR_GERAL", null);
    UUID message = message(global, "must remain private");
    var reporter = session("reader");
    var report =
        reports.report(
            "https://idp.invalid",
            "reader",
            reporter.getId(),
            message,
            UUID.randomUUID(),
            ReportReason.FRAUDE);
    var adminSession = session("evidence-admin");
    var opened =
        moderation.open(
            "https://idp.invalid", "evidence-admin", adminSession.getId(), report.reportId());

    var outsider = session("reader");
    assertThatThrownBy(
            () ->
                moderation.evidence(
                    "https://idp.invalid",
                    "reader",
                    outsider.getId(),
                    opened.caseId(),
                    "not authorized"))
        .isInstanceOf(ApiProblemException.class)
        .extracting(error -> ((ApiProblemException) error).status().value())
        .isEqualTo(403);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_evidence_access_fact", Integer.class))
        .isZero();
  }

  private void grant(UUID identity, String role, UUID scope) {
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,scope_ref,status) VALUES (?, ?, ?, 'VIGENTE')",
        identity,
        role,
        scope);
  }
}
