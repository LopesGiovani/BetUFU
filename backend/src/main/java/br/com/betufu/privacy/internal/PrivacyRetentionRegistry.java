package br.com.betufu.privacy.internal;

import br.com.betufu.privacy.PrivacyRestrictedRecordsPort.DataClass;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PrivacyRetentionRegistry {
  static final String POLICY = "conversation:2026-10-06:owner-retention-90-180";
  private final JdbcTemplate jdbc;

  public PrivacyRetentionRegistry(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  static Integer days(DataClass dataClass) {
    if (dataClass == null) return null;
    return switch (dataClass) {
      case COMMON_MESSAGES -> 90;
      case MODERATION_EVIDENCE -> 180;
      default -> null;
    };
  }

  boolean held(DataClass dataClass, UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS(SELECT FROM privacy_retention_hold_fact h
        WHERE h.data_class IN ('COMMON_MESSAGES','MODERATION_EVIDENCE') AND h.record_id=? AND h.action='HOLD'
        AND NOT EXISTS(SELECT FROM privacy_retention_hold_fact r WHERE r.releases_hold_id=h.hold_id))
        """,
            Boolean.class,
            id));
  }

  boolean suppressed(DataClass dataClass, UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM privacy_suppression_fact WHERE data_class=? AND record_id=?)",
            Boolean.class,
            dataClass.name(),
            id));
  }

  void lock() {
    jdbc.query("SELECT pg_advisory_xact_lock(724627)", rs -> {});
  }

  void record(DataClass dataClass, UUID id, UUID actor, String digest) {
    jdbc.update(
        """
        INSERT INTO privacy_suppression_fact(data_class,record_id,actor_identity_id,inventory_digest,policy_reference)
        VALUES (?,?,?,?,?) ON CONFLICT(data_class,record_id) DO NOTHING
        """,
        dataClass.name(),
        id,
        actor,
        digest,
        POLICY);
  }

  long cursor(DataClass type, UUID id) {
    return jdbc.queryForObject(
        "SELECT change_cursor FROM privacy_suppression_fact WHERE data_class=? AND record_id=?",
        Long.class,
        type.name(),
        id);
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  List<java.util.Map<String, Object>> currentRegister() {
    return jdbc.queryForList(
        "SELECT suppression_id,data_class,record_id,actor_identity_id,inventory_digest,policy_reference,recorded_at,change_cursor FROM privacy_suppression_fact ORDER BY change_cursor");
  }
}
