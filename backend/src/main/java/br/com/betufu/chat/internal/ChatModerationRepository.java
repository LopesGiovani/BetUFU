package br.com.betufu.chat.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatModerationRepository {
  private final JdbcTemplate jdbc;

  public ChatModerationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<CaseRow> cases() {
    return jdbc.query(
        "SELECT c.case_id,c.message_id,ch.event_id,c.status,c.opened_at FROM public.chat_moderation_case c JOIN public.chat_channel ch ON ch.channel_id=c.channel_id ORDER BY c.opened_at,c.case_id",
        this::caseRow);
  }

  List<PendingReport> pendingReports() {
    return jdbc.query(
        "SELECT r.report_id,ch.event_id FROM public.chat_report r JOIN public.chat_message m ON m.message_id=r.message_id JOIN public.chat_channel ch ON ch.channel_id=m.channel_id LEFT JOIN public.chat_moderation_case_report_fact cf ON cf.report_id=r.report_id WHERE cf.report_id IS NULL ORDER BY r.reported_at,r.report_id",
        (rs, row) -> new PendingReport(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)));
  }

  Optional<CaseRow> findCase(UUID caseId) {
    return jdbc
        .query(
            "SELECT c.case_id,c.message_id,ch.event_id,c.status,c.opened_at FROM public.chat_moderation_case c JOIN public.chat_channel ch ON ch.channel_id=c.channel_id WHERE c.case_id=?",
            this::caseRow,
            caseId)
        .stream()
        .findFirst();
  }

  Optional<ReportTarget> targetForReport(UUID reportId) {
    return jdbc.query(
        "SELECT c.event_id FROM public.chat_report r JOIN public.chat_message m ON m.message_id=r.message_id JOIN public.chat_channel c ON c.channel_id=m.channel_id WHERE r.report_id=?",
        rs -> {
          if (!rs.next()) return Optional.empty();
          return Optional.of(new ReportTarget(rs.getObject(1, UUID.class)));
        },
        reportId);
  }

  UUID ensureCase(UUID reportId) {
    var existing =
        jdbc
            .query(
                "SELECT case_id FROM public.chat_moderation_case_report_fact WHERE report_id=?",
                (rs, row) -> rs.getObject(1, UUID.class),
                reportId)
            .stream()
            .findFirst();
    if (existing.isPresent()) return existing.get();

    MessageRef message =
        jdbc.queryForObject(
            "SELECT r.message_id,m.channel_id FROM public.chat_report r JOIN public.chat_message m ON m.message_id=r.message_id WHERE r.report_id=?",
            (rs, row) -> new MessageRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
            reportId);
    UUID caseId =
        jdbc.queryForObject(
            "INSERT INTO public.chat_moderation_case(message_id,channel_id) VALUES (?,?) ON CONFLICT (message_id) DO UPDATE SET updated_at=CURRENT_TIMESTAMP(6) RETURNING case_id",
            UUID.class,
            message.messageId(),
            message.channelId());
    jdbc.update(
        "INSERT INTO public.chat_moderation_case_report_fact(case_id,report_id) VALUES (?,?) ON CONFLICT DO NOTHING",
        caseId,
        reportId);
    return caseId;
  }

  Optional<CaseRow> lockCase(UUID caseId) {
    return jdbc
        .query(
            "SELECT c.case_id,c.message_id,ch.event_id,c.status,c.opened_at FROM public.chat_moderation_case c JOIN public.chat_channel ch ON ch.channel_id=c.channel_id WHERE c.case_id=? FOR UPDATE",
            this::caseRow,
            caseId)
        .stream()
        .findFirst();
  }

  Optional<CommandFact> command(String table, UUID actor, UUID command) {
    if (!table.matches("chat_moderation_(decision|forward)_fact"))
      throw new IllegalArgumentException("unsupported moderation fact table");
    String timestamp =
        table.equals("chat_moderation_forward_fact") ? "forwarded_at" : "recorded_at";
    return jdbc
        .query(
            "SELECT case_id,client_command_id,reason,"
                + timestamp
                + " FROM public."
                + table
                + " WHERE actor_identity_id=? AND client_command_id=?",
            (rs, row) ->
                new CommandFact(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant()),
            actor,
            command)
        .stream()
        .findFirst();
  }

  Optional<DecisionFact> decision(UUID caseId, UUID actor, UUID command) {
    return jdbc
        .query(
            "SELECT decision_fact_id,decision_kind,reason,recorded_at FROM public.chat_moderation_decision_fact WHERE case_id=? AND actor_identity_id=? AND client_command_id=?",
            (rs, row) ->
                new DecisionFact(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant()),
            caseId,
            actor,
            command)
        .stream()
        .findFirst();
  }

  DecisionFact insertDecision(UUID caseId, UUID actor, UUID command, String kind, String reason) {
    jdbc.update(
        "INSERT INTO public.chat_moderation_decision_fact(case_id,actor_identity_id,decision_kind,reason,client_command_id) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
        caseId,
        actor,
        kind,
        reason,
        command);
    return decision(caseId, actor, command).orElseThrow();
  }

  RemovalFact remove(UUID caseId, UUID messageId, UUID actor, String reason) {
    jdbc.update(
        // Removal and message writes share one cursor domain so connected readers see old removals.
        "INSERT INTO public.chat_message_removal_fact(case_id,message_id,actor_identity_id,reason,change_cursor) OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,nextval('public.chat_message_change_cursor_seq')) ON CONFLICT (case_id) DO NOTHING",
        caseId,
        messageId,
        actor,
        reason);
    jdbc.update(
        "INSERT INTO public.chat_message_projection(message_id,state,rebuilt_at) VALUES (?,'REMOVED',CURRENT_TIMESTAMP(6)) ON CONFLICT (message_id) DO UPDATE SET state='REMOVED',rebuilt_at=CURRENT_TIMESTAMP(6)",
        messageId);
    jdbc.update(
        "UPDATE public.chat_moderation_case SET status='RESOLVED',updated_at=CURRENT_TIMESTAMP(6) WHERE case_id=?",
        caseId);
    return jdbc.queryForObject(
        "SELECT removal_fact_id,removed_at FROM public.chat_message_removal_fact WHERE case_id=?",
        (rs, row) -> new RemovalFact(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
        caseId);
  }

  Optional<ForwardFact> forward(UUID caseId, UUID actor, UUID command, String reason) {
    jdbc.update(
        "INSERT INTO public.chat_moderation_forward_fact(case_id,actor_identity_id,reason,client_command_id) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
        caseId,
        actor,
        reason,
        command);
    jdbc.update(
        "UPDATE public.chat_moderation_case SET status='ESCALATED',updated_at=CURRENT_TIMESTAMP(6) WHERE case_id=?",
        caseId);
    return jdbc
        .query(
            "SELECT forward_fact_id,forwarded_at FROM public.chat_moderation_forward_fact WHERE case_id=?",
            (rs, row) ->
                new ForwardFact(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
            caseId)
        .stream()
        .findFirst();
  }

  void recordEvidenceAccess(UUID caseId, UUID actor, String reason) {
    jdbc.update(
        "INSERT INTO public.chat_moderation_evidence_access_fact(case_id,actor_identity_id,access_reason) VALUES (?,?,?)",
        caseId,
        actor,
        reason);
  }

  EvidenceRow evidence(UUID caseId) {
    UUID messageId =
        jdbc.queryForObject(
            "SELECT message_id FROM chat_moderation_case WHERE case_id=?", UUID.class, caseId);
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM chat_privacy_suppression_projection WHERE data_class='MODERATION_EVIDENCE' AND message_id=?)",
            Boolean.class,
            messageId)))
      throw new br.com.betufu.platform.api.ApiProblemException(
          org.springframework.http.HttpStatus.GONE,
          br.com.betufu.platform.api.ApiErrorCode.NOT_FOUND);
    var header =
        jdbc.queryForObject(
            "SELECT c.case_id,c.message_id,m.author_identity_id,m.body FROM public.chat_moderation_case c JOIN public.chat_message m ON m.message_id=c.message_id WHERE c.case_id=?",
            (rs, row) ->
                new EvidenceHeader(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class),
                    rs.getString(4)),
            caseId);
    var reports =
        jdbc.query(
            "SELECT r.report_id,r.reporter_identity_id,r.reason,r.reported_at FROM public.chat_moderation_case_report_fact cf JOIN public.chat_report r ON r.report_id=cf.report_id WHERE cf.case_id=? ORDER BY r.reported_at,r.report_id",
            (rs, row) ->
                new ReportRow(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant()),
            caseId);
    return new EvidenceRow(header, reports);
  }

  private CaseRow caseRow(ResultSet rs, int row) throws SQLException {
    return new CaseRow(
        rs.getObject("case_id", UUID.class),
        rs.getObject("message_id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getString("status"),
        rs.getTimestamp("opened_at").toInstant());
  }

  record CaseRow(UUID caseId, UUID messageId, UUID eventId, String status, Instant openedAt) {}

  record MessageRef(UUID messageId, UUID channelId) {}

  record ReportTarget(UUID eventId) {}

  record PendingReport(UUID reportId, UUID eventId) {}

  record CommandFact(UUID caseId, UUID commandId, String reason, Instant recordedAt) {}

  record DecisionFact(UUID decisionId, String kind, String reason, Instant recordedAt) {}

  record RemovalFact(UUID removalId, Instant removedAt) {}

  record ForwardFact(UUID forwardId, Instant forwardedAt) {}

  record EvidenceHeader(UUID caseId, UUID messageId, UUID authorId, String body) {}

  record ReportRow(UUID reportId, UUID reporterId, String reason, Instant reportedAt) {}

  record EvidenceRow(EvidenceHeader header, List<ReportRow> reports) {}
}
