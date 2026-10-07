package br.com.betufu.privacy;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reuses the existing real OIDC/session/CSRF fixture and its inherited authorization regressions.
 */
class PrivacyExportIT extends PrivacyRightsHttpIT {
  @org.junit.jupiter.api.BeforeEach
  void globalChannel() {
    jdbc.update("INSERT INTO chat_channel(kind) VALUES ('GLOBAL') ON CONFLICT DO NOTHING");
  }

  @Test
  void nonemptyRestrictedExportsAreOwnerPrivateAndMissingProducersPreventCompletion()
      throws Exception {
    UUID message =
        jdbc.queryForObject(
            "INSERT INTO chat_message(channel_id,author_identity_id,body) SELECT channel_id,?,'holder text' FROM chat_channel WHERE kind='GLOBAL' RETURNING message_id",
            UUID.class,
            owner);
    jdbc.update(
        "INSERT INTO chat_message(channel_id,author_identity_id,body) SELECT channel_id,?,'third party secret' FROM chat_channel WHERE kind='GLOBAL'",
        other);
    jdbc.update(
        "INSERT INTO ufu_enrollment(identity_id,matricula_hmac,hmac_version,status) VALUES (?,decode(repeat('af',32),'hex'),1,'ATIVO')",
        owner);
    jdbc.update("INSERT INTO credit_account(identity_id) VALUES (?) ON CONFLICT DO NOTHING", owner);
    jdbc.update(
        "INSERT INTO credit_ledger_entry(identity_id,entry_type,direction,amount,source_type,source_id,idempotency_key) VALUES (?,'GRANT_INITIAL','CREDIT',1000.00,'SYSTEM',?,'export-fixture')",
        owner,
        owner);
    var account = session("owner");
    String caseId =
        json.readTree(call(account, "POST", "", accountBody("ACCESS"), true).body())
            .get("caseId")
            .asString();
    assertThat(call(account, "POST", "/" + caseId + "/exports", null, true).statusCode())
        .isEqualTo(403);
    assertThat(call(administrator, "POST", "/" + caseId + "/exports", null, false).statusCode())
        .isEqualTo(403);
    var created = call(administrator, "POST", "/" + caseId + "/exports", null, true);
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.body())
        .contains(
            "holder text",
            message.toString(),
            "ENROLLMENT",
            "ATIVO",
            "blockedClasses",
            "AUDIT",
            "LOGS",
            "BACKUPS")
        .doesNotContain("third party secret", "matricula_hmac", "subject", "issuer", "reporter");
    assertThat(json.readTree(created.body()).get("complete").asBoolean()).isFalse();
    assertThat(
            call(administrator, "POST", "/" + caseId + "/responses", responseBody("APPROVE"), true)
                .statusCode())
        .isEqualTo(409);
    var exported = call(account, "GET", "/" + caseId + "/exports/latest", null, true);
    assertThat(exported.statusCode()).isEqualTo(200);
    assertThat(exported.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(
            call(session("other"), "GET", "/" + caseId + "/exports/latest", null, true)
                .statusCode())
        .isEqualTo(403);
    assertThat(call(null, "GET", "/" + caseId + "/exports/latest", null, true).statusCode())
        .isEqualTo(401);
    sessions.deleteById(account.getId());
    assertThat(call(account, "GET", "/" + caseId + "/exports/latest", null, true).statusCode())
        .isEqualTo(401);
  }

  @Test
  void removedOriginalIsRedactedInNewAndPreviouslySavedExportsWithoutLosingReviewState()
      throws Exception {
    UUID message =
        jdbc.queryForObject(
            "INSERT INTO chat_message(channel_id,author_identity_id,body) SELECT channel_id,?,'moderation restricted original' FROM chat_channel WHERE kind='GLOBAL' RETURNING message_id",
            UUID.class,
            owner);
    var account = session("owner");
    String caseId =
        json.readTree(call(account, "POST", "", accountBody("ACCESS"), true).body())
            .get("caseId")
            .asString();
    assertThat(
            call(administrator, "POST", "/" + caseId + "/responses", responseBody("ANALYZE"), true)
                .statusCode())
        .isEqualTo(200);
    assertThat(call(administrator, "POST", "/" + caseId + "/exports", null, true).body())
        .contains("moderation restricted original");
    UUID moderation =
        jdbc.queryForObject(
            "INSERT INTO chat_moderation_case(message_id,channel_id,status) SELECT message_id,channel_id,'RESOLVED' FROM chat_message WHERE message_id=? RETURNING case_id",
            UUID.class,
            message);
    jdbc.update(
        "INSERT INTO chat_message_removal_fact(case_id,message_id,actor_identity_id,reason) VALUES (?,?,?,'restricted moderator reason')",
        moderation,
        message,
        admin);
    assertThat(call(account, "GET", "/" + caseId + "/exports/latest", null, true).body())
        .contains("[MODERATED]")
        .doesNotContain("moderation restricted original", "restricted moderator reason");
    assertThat(call(administrator, "POST", "/" + caseId + "/exports", null, true).body())
        .contains("[MODERATED]")
        .doesNotContain("moderation restricted original");
    assertThat(call(account, "GET", "/" + caseId, null, true).body())
        .contains("UNDER_REVIEW", "EXPORT_READY");
  }

  @Test
  void verifiedAthleteExportNeverExposesProofHmacOrAnotherAthlete() throws Exception {
    UUID athlete =
        jdbc.queryForObject(
            "INSERT INTO sports_athlete(name,slug) VALUES ('Verified Athlete','verified-athlete') RETURNING athlete_id",
            UUID.class);
    jdbc.update(
        "INSERT INTO sports_athlete(name,slug) VALUES ('Other Athlete Secret','other-secret')");
    String caseId =
        json.readTree(
                call(
                        administrator,
                        "POST",
                        "",
                        athleteBody(athlete, "restricted-external-proof"),
                        true)
                    .body())
            .get("caseId")
            .asString();
    var result = call(administrator, "POST", "/" + caseId + "/exports", null, true);
    assertThat(result.statusCode()).isEqualTo(201);
    assertThat(result.body())
        .contains("Verified Athlete", "ATHLETE_WITHOUT_ACCOUNT")
        .doesNotContain("restricted-external-proof", "Other Athlete Secret", "hmac");
    assertThat(
            call(session("owner"), "GET", "/" + caseId + "/exports/latest", null, true)
                .statusCode())
        .isEqualTo(403);
    jdbc.update("UPDATE operational_role_grant SET status='REVOGADO' WHERE identity_id=?", admin);
    assertThat(
            call(administrator, "GET", "/" + caseId + "/exports/latest", null, true).statusCode())
        .isEqualTo(403);
  }

  @Test
  void savedExportIsRedactedAgainstSuppressionsRecordedAfterItsSnapshot() throws Exception {
    UUID message =
        jdbc.queryForObject(
            "INSERT INTO chat_message(channel_id,author_identity_id,body) SELECT channel_id,?,'text before snapshot' FROM chat_channel WHERE kind='GLOBAL' RETURNING message_id",
            UUID.class,
            owner);
    var account = session("owner");
    String caseId =
        json.readTree(call(account, "POST", "", accountBody("ACCESS"), true).body())
            .get("caseId")
            .asString();
    assertThat(call(administrator, "POST", "/" + caseId + "/exports", null, true).body())
        .contains("text before snapshot");
    // This fixture connection is the isolated migration owner, not the runtime account.
    jdbc.update(
        "INSERT INTO privacy_suppression_fact(data_class,record_id,actor_identity_id,inventory_digest,policy_reference) VALUES ('COMMON_MESSAGES',?,?,?,'fixture-isolated')",
        message,
        admin,
        "a".repeat(64));
    jdbc.update(
        "INSERT INTO chat_privacy_suppression_projection(data_class,message_id,change_cursor) SELECT data_class,record_id,change_cursor FROM privacy_suppression_fact");
    var download = call(account, "GET", "/" + caseId + "/exports/latest", null, true);
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.body()).contains("[SUPPRESSED]").doesNotContain("text before snapshot");
  }
}
