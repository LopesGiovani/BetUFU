package br.com.betufu.chat.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatSanctionRepository {
  private final JdbcTemplate jdbc;
  private final ChatWriteService.ChatWriteRepository writes;

  public ChatSanctionRepository(JdbcTemplate jdbc, ChatWriteService.ChatWriteRepository writes) {
    this.jdbc = jdbc;
    this.writes = writes;
  }

  void lockCommandActor(UUID actor) {
    // Command uniqueness spans cases; serialize that namespace before taking the target lock.
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,17))", rs -> {}, actor.toString());
  }

  UUID target(UUID caseId) {
    return jdbc
        .query(
            "SELECT m.author_identity_id FROM chat_moderation_case c JOIN chat_message m ON m.message_id=c.message_id WHERE c.case_id=?",
            (rs, row) -> rs.getObject(1, UUID.class),
            caseId)
        .stream()
        .findFirst()
        .orElseThrow(ChatCommunityModels::missing);
  }

  void lockTarget(UUID target) {
    writes.lockActor(target);
  }

  void lockCase(UUID caseId) {
    if (jdbc.query(
            "SELECT case_id FROM chat_moderation_case WHERE case_id=? FOR UPDATE",
            (rs, row) -> rs.getObject(1, UUID.class),
            caseId)
        .isEmpty()) throw ChatCommunityModels.missing();
  }

  private static final String SANCTIONS =
      "SELECT s.*,EXISTS(SELECT FROM chat_moderation_review_fact r WHERE r.case_id=s.case_id AND r.review_kind='REVERSED') AS annulled FROM chat_moderation_sanction_fact s ";

  Optional<Sanction> sanction(UUID id) {
    return one(SANCTIONS + "WHERE s.sanction_fact_id=? AND s.sanction_kind<>'LIFT'", id);
  }

  Optional<Sanction> forCase(UUID id) {
    return one(SANCTIONS + "WHERE s.case_id=? AND s.sanction_kind<>'LIFT'", id);
  }

  Optional<Sanction> command(UUID actor, UUID key) {
    return one(SANCTIONS + "WHERE s.actor_identity_id=? AND s.client_command_id=?", actor, key);
  }

  List<Sanction> forOwner(UUID owner) {
    return jdbc.query(
        SANCTIONS
            + "WHERE s.target_identity_id=? AND s.sanction_kind<>'LIFT' ORDER BY s.effective_from,s.sanction_fact_id",
        this::sanctionRow,
        owner);
  }

  private Optional<Sanction> one(String sql, Object... args) {
    return jdbc.query(sql, this::sanctionRow, args).stream().findFirst();
  }

  Sanction insert(
      UUID caseId,
      UUID admin,
      UUID target,
      ChatModerationModels.SanctionCommand command,
      Instant now,
      Instant until) {
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO chat_moderation_sanction_fact(case_id,actor_identity_id,target_identity_id,sanction_kind,reason,client_command_id,effective_from,effective_until,recorded_at) VALUES (?,?,?,?,?,?,?,?,?) RETURNING sanction_fact_id",
            UUID.class,
            caseId,
            admin,
            target,
            command.kind().name(),
            command.reason(),
            command.clientCommandId(),
            Timestamp.from(now),
            until == null ? null : Timestamp.from(until),
            Timestamp.from(now));
    jdbc.update(
        "UPDATE chat_moderation_case SET status='RESOLVED',updated_at=CURRENT_TIMESTAMP WHERE case_id=?",
        caseId);
    return sanction(id).orElseThrow();
  }

  Optional<Review> review(UUID id) {
    return reviewOne("WHERE review_fact_id=?", id);
  }

  Optional<Review> reviewCommand(UUID actor, UUID key) {
    return reviewOne("WHERE actor_identity_id=? AND client_command_id=?", actor, key);
  }

  Optional<Review> opened(UUID caseId) {
    return reviewOne("WHERE case_id=? AND review_kind='OPENED'", caseId);
  }

  Optional<Review> resolution(UUID caseId) {
    return reviewOne("WHERE case_id=? AND review_kind IN ('REVIEWED','REVERSED')", caseId);
  }

  List<Review> pendingReviews() {
    return jdbc.query(
        "SELECT r.* FROM chat_moderation_review_fact r WHERE review_kind='OPENED' AND NOT EXISTS(SELECT FROM chat_moderation_review_fact d WHERE d.case_id=r.case_id AND d.review_kind IN ('REVIEWED','REVERSED')) ORDER BY recorded_at,review_fact_id",
        this::reviewRow);
  }

  private Optional<Review> reviewOne(String where, Object... args) {
    return jdbc
        .query("SELECT * FROM chat_moderation_review_fact " + where, this::reviewRow, args)
        .stream()
        .findFirst();
  }

  Review insertReview(UUID caseId, UUID actor, UUID key, String kind, String reason, Instant now) {
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO chat_moderation_review_fact(case_id,actor_identity_id,client_command_id,review_kind,reason,recorded_at) VALUES (?,?,?,?,?,?) RETURNING review_fact_id",
            UUID.class,
            caseId,
            actor,
            key,
            kind,
            reason,
            Timestamp.from(now));
    jdbc.update(
        "UPDATE chat_moderation_case SET status=?,updated_at=CURRENT_TIMESTAMP WHERE case_id=?",
        kind.equals("OPENED") ? "IN_REVIEW" : "RESOLVED",
        caseId);
    return review(id).orElseThrow();
  }

  String reviewStatus(UUID caseId) {
    var decision = resolution(caseId);
    if (decision.isPresent())
      return decision.get().kind().equals("REVERSED") ? "ANNULLED" : "MAINTAINED";
    return opened(caseId).isPresent() ? "PENDING" : "NONE";
  }

  private Sanction sanctionRow(ResultSet rs, int row) throws SQLException {
    var until = rs.getTimestamp("effective_until");
    return new Sanction(
        rs.getObject("sanction_fact_id", UUID.class),
        rs.getObject("case_id", UUID.class),
        rs.getObject("actor_identity_id", UUID.class),
        rs.getObject("target_identity_id", UUID.class),
        ChatModerationModels.SanctionKind.valueOf(rs.getString("sanction_kind")),
        rs.getString("reason"),
        rs.getObject("client_command_id", UUID.class),
        rs.getTimestamp("effective_from").toInstant(),
        until == null ? null : until.toInstant(),
        rs.getBoolean("annulled"));
  }

  private Review reviewRow(ResultSet rs, int row) throws SQLException {
    return new Review(
        rs.getObject("review_fact_id", UUID.class),
        rs.getObject("case_id", UUID.class),
        rs.getObject("actor_identity_id", UUID.class),
        rs.getObject("client_command_id", UUID.class),
        rs.getString("review_kind"),
        rs.getString("reason"),
        rs.getTimestamp("recorded_at").toInstant());
  }

  record Sanction(
      UUID id,
      UUID caseId,
      UUID actor,
      UUID target,
      ChatModerationModels.SanctionKind kind,
      String reason,
      UUID key,
      Instant from,
      Instant until,
      boolean annulled) {
    boolean active(Instant now) {
      return !annulled && !now.isBefore(from) && (until == null || now.isBefore(until));
    }
  }

  record Review(
      UUID id, UUID caseId, UUID actor, UUID key, String kind, String reason, Instant recordedAt) {}
}
