package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.chat.internal.ChatModerationService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import tools.jackson.databind.JsonNode;

class ChatSanctionIT extends ChatSanctionFixture {
  @Test
  void administratorConfirmsAndOnlyWritingIsBlockedAcrossChannels() throws Exception {
    jdbc.update("INSERT INTO credit_account(identity_id,balance) VALUES (?,123.00)", author);
    jdbc.update("INSERT INTO ranking_projection(identity_id,wins,losses) VALUES (?,7,2)", author);
    jdbc.update(
        "INSERT INTO credit_ledger_entry(identity_id,entry_type,direction,amount,source_type,idempotency_key) VALUES (?,'GRANT_INITIAL','CREDIT',123.00,'SYSTEM','sanction-fixture')",
        author);
    jdbc.update(
        "INSERT INTO prediction(identity_id,market_id,market_version_id,publication_id,rule_version,selection,stake,accepted_odds,potential_return,net_profit,close_deadline,idempotency_key,payload_digest) VALUES (?,?,?,?, 'fixture-v1','HOME',10.00,2.0,20.00,10.00,CURRENT_TIMESTAMP,'sanction-fixture','fixture-digest')",
        author,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID());
    String financial = financialSnapshot();
    UUID occurrence = occurrence();
    var response = confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID());
    assertThat(response.statusCode()).isEqualTo(201);
    var fact = json.readTree(response.body());
    assertThat(Instant.parse(fact.get("effectiveUntil").asString()))
        .isEqualTo(Instant.parse(fact.get("effectiveFrom").asString()).plusSeconds(86400));
    assertThat(post(ownerSession, null, UUID.randomUUID(), "blocked globally").statusCode())
        .isEqualTo(403);
    assertThat(post(ownerSession, event(true), UUID.randomUUID(), "blocked in event").statusCode())
        .isEqualTo(403);
    assertThat(call(ownerSession, "GET", "/global", null, true).statusCode()).isEqualTo(200);
    assertThat(call(ownerSession, "GET", "/me/sanctions", null, true).body())
        .contains("CHAT_SUSPENSION_24H", "confirmed violation")
        .doesNotContain(author.toString());
    assertThat(financialSnapshot()).isEqualTo(financial);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void moderatorAndRevokedAdministratorCannotConfirmEvenOnReplay() throws Exception {
    UUID moderator = participant("moderator");
    grant(moderator, "MODERADOR", UUID.randomUUID());
    UUID occurrence = occurrence(), key = UUID.randomUUID();
    assertThat(
            call(
                    session("moderator"),
                    "POST",
                    sanctionPath(occurrence),
                    confirmBody("CHAT_SUSPENSION_24H", key),
                    true)
                .statusCode())
        .isEqualTo(403);
    assertThat(confirm(occurrence, "CHAT_SUSPENSION_24H", key).statusCode()).isEqualTo(201);
    jdbc.update(
        "UPDATE operational_role_grant SET status='REVOGADO',revoked_at=CURRENT_TIMESTAMP WHERE identity_id=?",
        admin);
    assertThat(confirm(occurrence, "CHAT_SUSPENSION_24H", key).statusCode()).isEqualTo(403);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void duplicateOccurrenceAndExpiredPunishmentDoNotResetProgression() throws Exception {
    UUID first = occurrence(), key = UUID.randomUUID();
    var original = confirm(first, "CHAT_SUSPENSION_24H", key);
    assertThat(original.statusCode()).isEqualTo(201);
    assertThat(confirm(first, "CHAT_SUSPENSION_24H", key).body()).isEqualTo(original.body());
    assertThat(confirm(first, "CHAT_SUSPENSION_7D", UUID.randomUUID()).statusCode()).isEqualTo(409);
    clock.advanceSeconds(86400);
    assertThat(confirm(occurrence(), "CHAT_SUSPENSION_7D", UUID.randomUUID()).statusCode())
        .isEqualTo(201);
    clock.advanceSeconds(7 * 86400);
    var ban = confirm(occurrence(), "CHAT_BAN", UUID.randomUUID());
    assertThat(ban.statusCode()).isEqualTo(201);
    assertThat(json.readTree(ban.body()).get("effectiveUntil").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void exactExpirationAllowsNewWritingAndHistoryRetainsThePunishment() throws Exception {
    assertThat(confirm(occurrence(), "CHAT_SUSPENSION_24H", UUID.randomUUID()).statusCode())
        .isEqualTo(201);
    clock.advanceSeconds(86400 - 1);
    assertThat(post(ownerSession, null, UUID.randomUUID(), "one second early").statusCode())
        .isEqualTo(403);
    clock.advanceSeconds(1);
    assertThat(post(ownerSession, null, UUID.randomUUID(), "at exact expiry").statusCode())
        .isEqualTo(201);
    assertThat(call(ownerSession, "GET", "/me/sanctions", null, true).body())
        .contains("CHAT_SUSPENSION_24H");
  }

  @Test
  void serverOwnsTheTargetAndRequiresCurrentSessionOriginAndCsrf() throws Exception {
    UUID occurrence = occurrence();
    String body = confirmBody("CHAT_SUSPENSION_24H", UUID.randomUUID());
    assertThat(call(adminSession, "POST", sanctionPath(occurrence), body, false).statusCode())
        .isEqualTo(403);
    assertThat(call(null, "POST", sanctionPath(occurrence), body, true).statusCode())
        .isEqualTo(401);
    assertThat(
            call(
                    adminSession,
                    "POST",
                    sanctionPath(occurrence),
                    body.replace("}", ",\"targetIdentityId\":\"" + admin + "\"}"),
                    true)
                .statusCode())
        .isEqualTo(400);
    sessions.deleteById(adminSession.getId());
    assertThat(confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID()).statusCode())
        .isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
  }
}

abstract class ChatSanctionFixture extends ChatWriteFixture {
  @Autowired ChatModerationService moderation;
  UUID admin;
  Session adminSession, ownerSession;

  @BeforeEach
  void sanctions() {
    // Keep clock advances behind real session time to isolate punishment expiry from idle expiry.
    clock.current =
        Instant.now().minusSeconds(30 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    admin = participant("sanction-admin");
    grant(admin, "ADMINISTRADOR_GERAL", null);
    adminSession = session("sanction-admin");
    ownerSession = session("reader");
  }

  UUID participant(String subject) {
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid',?) RETURNING identity_id",
            UUID.class,
            subject);
    jdbc.update("INSERT INTO betufu_profile(identity_id,nickname) VALUES (?,?)", id, subject);
    return id;
  }

  void grant(UUID actor, String role, UUID scope) {
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,scope_ref,status) VALUES (?,?,?,'VIGENTE')",
        actor,
        role,
        scope);
  }

  UUID occurrence() {
    UUID message = message(global, "historical violation");
    UUID report =
        jdbc.queryForObject(
            "INSERT INTO chat_report(reporter_identity_id,message_id,reason) VALUES (?,?,'OFENSA') RETURNING report_id",
            UUID.class,
            admin,
            message);
    return moderation
        .open("https://idp.invalid", "sanction-admin", adminSession.getId(), report)
        .caseId();
  }

  String sanctionPath(UUID occurrence) {
    return "/moderation/cases/" + occurrence + "/sanctions";
  }

  String confirmBody(String kind, UUID key) throws Exception {
    return json.writeValueAsString(
        Map.of("kind", kind, "reason", "confirmed violation", "clientCommandId", key));
  }

  HttpResponse<String> confirm(UUID occurrence, String kind, UUID key) throws Exception {
    return call(adminSession, "POST", sanctionPath(occurrence), confirmBody(kind, key), true);
  }

  JsonNode sanction(UUID occurrence) throws Exception {
    var response = confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body());
  }

  String reviewBody(UUID key) throws Exception {
    return json.writeValueAsString(
        Map.of("clientCommandId", key, "reason", "please review this punishment"));
  }

  HttpResponse<String> review(UUID sanction, UUID key) throws Exception {
    return call(
        ownerSession, "POST", "/me/sanctions/" + sanction + "/reviews", reviewBody(key), true);
  }

  HttpResponse<String> decide(UUID review, String decision, UUID key) throws Exception {
    return call(
        adminSession,
        "POST",
        "/moderation/reviews/" + review + "/decisions",
        json.writeValueAsString(
            Map.of("clientCommandId", key, "reason", "evidence reviewed", "decision", decision)),
        true);
  }

  String financialSnapshot() {
    return jdbc.queryForList("SELECT * FROM credit_account ORDER BY identity_id").toString()
        + jdbc.queryForList("SELECT * FROM credit_ledger_entry ORDER BY entry_id").toString()
        + jdbc.queryForList("SELECT * FROM ranking_projection ORDER BY identity_id").toString()
        + jdbc.queryForList("SELECT * FROM prediction ORDER BY prediction_id").toString();
  }

  HttpResponse<String> call(Session actor, String method, String path, String body, boolean csrf)
      throws Exception {
    String cookie = "XSRF-TOKEN=" + TOKEN;
    if (actor != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder().encodeToString(actor.getId().getBytes(StandardCharsets.UTF_8));
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat" + path))
            .header("Cookie", cookie)
            .header("Origin", ORIGIN)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
    if (csrf) builder.header("X-XSRF-TOKEN", TOKEN);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try (var client = HttpClient.newHttpClient()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
