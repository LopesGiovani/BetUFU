package br.com.betufu.chat.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatReportRepository {
  private final JdbcTemplate jdbc;

  public ChatReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Target> target(UUID message) {
    return jdbc
        .query(
            "SELECT c.kind,c.event_id FROM public.chat_message m JOIN public.chat_channel c ON c.channel_id=m.channel_id WHERE m.message_id=?",
            (rs, row) -> new Target(rs.getString(1), rs.getObject(2, UUID.class)),
            message)
        .stream()
        .findFirst();
  }

  Optional<Report> receipt(UUID actor, UUID key) {
    return jdbc
        .query(
            "SELECT r.* FROM public.chat_report_receipt c JOIN public.chat_report r ON r.report_id=c.report_id WHERE c.reporter_identity_id=? AND c.idempotency_key=?",
            this::report,
            actor,
            key)
        .stream()
        .findFirst();
  }

  Optional<Report> occurrence(UUID actor, UUID message) {
    return jdbc
        .query(
            "SELECT * FROM public.chat_report WHERE reporter_identity_id=? AND message_id=?",
            this::report,
            actor,
            message)
        .stream()
        .findFirst();
  }

  Report insert(UUID actor, UUID message, ChatCommunityModels.ReportReason reason) {
    return jdbc.queryForObject(
        "INSERT INTO public.chat_report(reporter_identity_id,message_id,reason) VALUES (?,?,?) RETURNING *",
        this::report,
        actor,
        message,
        reason.name());
  }

  void receipt(UUID actor, UUID key, UUID report) {
    jdbc.update(
        "INSERT INTO public.chat_report_receipt(reporter_identity_id,idempotency_key,report_id) VALUES (?,?,?)",
        actor,
        key,
        report);
  }

  private Report report(ResultSet rs, int row) throws SQLException {
    return new Report(
        new ChatCommunityModels.ReportReceipt(
            rs.getObject("report_id", UUID.class), rs.getTimestamp("reported_at").toInstant()),
        rs.getObject("message_id", UUID.class),
        ChatCommunityModels.ReportReason.valueOf(rs.getString("reason")));
  }

  record Target(String kind, UUID eventId) {}

  record Report(
      ChatCommunityModels.ReportReceipt receipt,
      UUID messageId,
      ChatCommunityModels.ReportReason reason) {}
}
