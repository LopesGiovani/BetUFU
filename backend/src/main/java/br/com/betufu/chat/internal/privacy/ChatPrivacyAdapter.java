package br.com.betufu.chat.internal.privacy;

import br.com.betufu.chat.ChatRulesPreferencePort;
import br.com.betufu.chat.internal.ChatPushService;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatPrivacyAdapter implements PrivacyRestrictedRecordsPort.Chat {
  private final JdbcTemplate jdbc;
  private final ObjectProvider<ChatPushService> push;
  private final ObjectProvider<ChatRulesPreferencePort> rulesPreferences;

  public ChatPrivacyAdapter(JdbcTemplate jdbc, ObjectProvider<ChatPushService> push) {
    this(jdbc, push, null);
  }

  @Autowired
  public ChatPrivacyAdapter(
      JdbcTemplate jdbc,
      ObjectProvider<ChatPushService> push,
      ObjectProvider<ChatRulesPreferencePort> rulesPreferences) {
    this.jdbc = jdbc;
    this.push = push;
    this.rulesPreferences = rulesPreferences;
  }

  @Override
  public Set<DataClass> supportedClasses() {
    return Set.of(
        DataClass.COMMON_MESSAGES, DataClass.MODERATION_EVIDENCE, DataClass.SANCTION_PROGRESSION);
  }

  @Override
  public List<RestrictedRecord> export(Subject subject, DataClass dataClass) {
    if (!supportedClasses().contains(dataClass))
      throw new IllegalArgumentException("Unsupported class");
    if (subject.identityId() == null) return List.of();
    return switch (dataClass) {
      case COMMON_MESSAGES ->
          jdbc.query(
              "SELECT message_id,body,posted_at FROM chat_message WHERE author_identity_id=? ORDER BY posted_at,message_id",
              (rs, row) ->
                  new RestrictedRecord(
                      rs.getString(1),
                      Map.of(
                          "body",
                          java.util.Optional.ofNullable(servingMarker(rs.getObject(1, UUID.class)))
                              .orElse(rs.getString(2)),
                          "postedAt",
                          rs.getTimestamp(3).toInstant().toString())),
              subject.identityId());
      // No original body, reporters, moderators or third-party reasons are included in holder
      // exports.
      case MODERATION_EVIDENCE ->
          jdbc.query(
              "SELECT c.case_id,c.status,c.opened_at FROM chat_moderation_case c JOIN chat_message m ON m.message_id=c.message_id WHERE m.author_identity_id=? ORDER BY c.case_id",
              (rs, row) ->
                  new RestrictedRecord(
                      rs.getString(1),
                      Map.of(
                          "state",
                          rs.getString(2),
                          "openedAt",
                          rs.getTimestamp(3).toInstant().toString())),
              subject.identityId());
      case SANCTION_PROGRESSION ->
          jdbc.query(
              "SELECT sanction_fact_id,sanction_kind,effective_from FROM chat_moderation_sanction_fact WHERE target_identity_id=? ORDER BY effective_from,sanction_fact_id",
              (rs, row) ->
                  new RestrictedRecord(
                      rs.getString(1),
                      Map.of(
                          "kind",
                          rs.getString(2),
                          "effectiveFrom",
                          rs.getTimestamp(3).toInstant().toString())),
              subject.identityId());
      default -> throw new IllegalArgumentException("Unsupported class");
    };
  }

  @Override
  public List<Target> retentionTargets() {
    return jdbc
        .query(
            """
        SELECT m.message_id,m.posted_at,c.case_id,c.status,
          GREATEST((SELECT max(d.recorded_at) FROM chat_moderation_decision_fact d WHERE d.case_id=c.case_id AND d.decision_kind IN ('NO_ACTION','REMOVE')),
            (SELECT max(r.removed_at) FROM chat_message_removal_fact r WHERE r.case_id=c.case_id),
            (SELECT max(s.recorded_at) FROM chat_moderation_sanction_fact s WHERE s.case_id=c.case_id AND s.sanction_kind<>'LIFT'),
            (SELECT max(v.recorded_at) FROM chat_moderation_review_fact v WHERE v.case_id=c.case_id AND v.review_kind IN ('REVIEWED','REVERSED'))) AS closed_at,
          EXISTS(SELECT FROM chat_report r WHERE r.message_id=m.message_id AND NOT EXISTS(SELECT FROM chat_moderation_case_report_fact cf WHERE cf.report_id=r.report_id)) AS pending_report,
          EXISTS(SELECT FROM chat_moderation_review_fact v WHERE v.case_id=c.case_id AND v.review_kind='OPENED'
            AND NOT EXISTS(SELECT FROM chat_moderation_review_fact d WHERE d.case_id=v.case_id AND d.review_kind IN ('REVIEWED','REVERSED') AND d.recorded_at>=v.recorded_at)) AS pending_review
        FROM chat_message m LEFT JOIN chat_moderation_case c ON c.message_id=m.message_id ORDER BY m.message_id
        """,
            (rs, row) -> {
              boolean evidence = rs.getObject("case_id") != null;
              var closed = rs.getTimestamp("closed_at");
              boolean blocked =
                  rs.getBoolean("pending_report")
                      || rs.getBoolean("pending_review")
                      || evidence && (!"RESOLVED".equals(rs.getString("status")) || closed == null);
              UUID id = rs.getObject("message_id", UUID.class);
              var common =
                  new Target(
                      DataClass.COMMON_MESSAGES,
                      id,
                      rs.getTimestamp("posted_at").toInstant(),
                      blocked);
              return evidence
                  ? List.of(
                      common,
                      new Target(
                          DataClass.MODERATION_EVIDENCE,
                          id,
                          closed == null ? null : closed.toInstant(),
                          blocked))
                  : List.of(common);
            })
        .stream()
        .flatMap(List::stream)
        .toList();
  }

  @Override
  public RestrictedRecord redactForServing(DataClass dataClass, RestrictedRecord record) {
    String marker =
        dataClass == DataClass.COMMON_MESSAGES
            ? servingMarker(UUID.fromString(record.reference()))
            : null;
    if (marker != null) {
      var fields = new java.util.HashMap<>(record.fields());
      fields.put("body", marker);
      return new RestrictedRecord(record.reference(), fields);
    }
    return record;
  }

  private String servingMarker(UUID id) {
    if (commonMessage(id).isPresent()) return "[SUPPRESSED]";
    boolean moderated =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT FROM chat_message_removal_fact WHERE message_id=?) OR EXISTS(SELECT FROM chat_message_projection WHERE message_id=? AND state='REMOVED')",
                Boolean.class,
                id,
                id));
    return moderated ? "[MODERATED]" : null;
  }

  @Override
  public void suppressServing(DataClass type, UUID messageId, long cursor) {
    if (type != DataClass.COMMON_MESSAGES && type != DataClass.MODERATION_EVIDENCE)
      throw new IllegalArgumentException("Unsupported class");
    jdbc.update(
        "INSERT INTO chat_privacy_suppression_projection(data_class,message_id,change_cursor) VALUES (?,?,?) ON CONFLICT DO NOTHING",
        type.name(),
        messageId,
        cursor);
  }

  record Suppression(UUID messageId, long cursor) {}

  private java.util.Optional<Suppression> commonMessage(UUID id) {
    return jdbc
        .query(
            "SELECT message_id,max(change_cursor) FROM chat_privacy_suppression_projection WHERE message_id=? GROUP BY message_id",
            (rs, row) -> new Suppression(rs.getObject(1, UUID.class), rs.getLong(2)),
            id)
        .stream()
        .findFirst();
  }

  public boolean evidenceSuppressed(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM chat_privacy_suppression_projection WHERE data_class='MODERATION_EVIDENCE' AND message_id=?)",
            Boolean.class,
            id));
  }

  public List<Suppression> commonChanges(long after) {
    return jdbc.query(
        "SELECT message_id,max(change_cursor) AS cursor FROM chat_privacy_suppression_projection WHERE change_cursor>? GROUP BY message_id ORDER BY cursor",
        (rs, row) -> new Suppression(rs.getObject(1, UUID.class), rs.getLong(2)),
        after);
  }

  public long latestCommonCursor() {
    return jdbc.queryForObject(
        "SELECT COALESCE(max(change_cursor),0) FROM chat_privacy_suppression_projection",
        Long.class);
  }

  /**
   * Exposes only the boolean preference to privacy workflows; no popup text or account id leaks.
   */
  public boolean rulesDismissed(UUID identityId) {
    if (identityId == null) return false;
    if (rulesPreferences != null) {
      var port = rulesPreferences.getIfAvailable();
      if (port != null) return port.dismissed(identityId);
    }
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chat_rules_dismissal WHERE owner_identity_id=?)",
            Boolean.class,
            identityId));
  }

  @Override
  public void lockRetentionTargets() {
    // V46 producer triggers share this lock before changing any retention eligibility input.
    jdbc.query("SELECT pg_advisory_xact_lock(724627)", rs -> {});
  }

  @Override
  public void servingChanged(UUID messageId) {
    var service = push.getIfAvailable();
    if (service == null) return;
    UUID event =
        jdbc.queryForObject(
            "SELECT c.event_id FROM chat_message m JOIN chat_channel c ON c.channel_id=m.channel_id WHERE m.message_id=?",
            UUID.class,
            messageId);
    service.changed(event);
  }
}
