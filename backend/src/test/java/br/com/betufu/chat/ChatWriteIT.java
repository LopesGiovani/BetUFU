package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatWriteService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.session.Session;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ChatWriteIT extends ChatWriteFixture {
  @Test
  void rebuildingLimiterDoesNotEraseTheIntervalOrAcceptedReceipts() throws Exception {
    Session account = session("reader");
    UUID key = UUID.randomUUID();
    var ack = post(account, null, key, "first");
    assertThat(ack.statusCode()).isEqualTo(201);
    jdbc.update("DELETE FROM chat_write_limiter");
    assertThat(post(account, null, UUID.randomUUID(), "second").statusCode()).isEqualTo(429);
    assertThat(post(account, null, key, "first").body()).isEqualTo(ack.body());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void administratorRoleNeverSubstitutesForActiveEnrollment() throws Exception {
    Session account = session("reader");
    org.springframework.security.core.context.SecurityContext original =
        account.getAttribute("SPRING_SECURITY_CONTEXT");
    var principal =
        (org.springframework.security.oauth2.core.oidc.user.OidcUser)
            original.getAuthentication().getPrincipal();
    var changed =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    changed.setAuthentication(
        new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
            principal,
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_ADMINISTRADOR_GERAL")),
            "keycloak"));
    account.setAttribute("SPRING_SECURITY_CONTEXT", changed);
    ((org.springframework.session.SessionRepository) sessions).save(account);
    jdbc.update("UPDATE ufu_enrollment SET status='LIBERADO' WHERE identity_id=?", author);
    assertThat(post(account, null, UUID.randomUUID(), "admin").statusCode()).isEqualTo(403);
  }

  @Test
  void rollbackCreatesNeitherReceiptNorLimiterDebitAndDoesNotNotify() throws Exception {
    Session account = session("reader");
    try (Wire wire = connected(account)) {
      wire.subscribe("/chat/global", "0");
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                writes.write(
                    "https://idp.invalid",
                    "reader",
                    account.getId(),
                    null,
                    UUID.randomUUID(),
                    "rolled back");
                status.setRollbackOnly();
              });
      assertThat(wire.frames.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
          .isZero();
      assertThat(post(account, null, UUID.randomUUID(), "accepted").statusCode()).isEqualTo(201);
    }
  }

  @Test
  void actualSessionActiveEnrollmentCsrfAndOriginAreRequired() throws Exception {
    assertThat(post(null, null, UUID.randomUUID(), "hello").statusCode()).isEqualTo(401);
    Session account = session("reader");
    jdbc.update("UPDATE ufu_enrollment SET status='LIBERADO' WHERE identity_id=?", author);
    assertThat(post(account, null, UUID.randomUUID(), "hello").statusCode()).isEqualTo(403);
    jdbc.update("DELETE FROM ufu_enrollment WHERE identity_id=?", author);
    assertThat(post(account, null, UUID.randomUUID(), "hello").statusCode()).isEqualTo(403);
    enroll();
    assertThat(
            request(account, null, UUID.randomUUID(), "{\"text\":\"hello\"}", false, ORIGIN)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            request(
                    account,
                    null,
                    UUID.randomUUID(),
                    "{\"text\":\"hello\"}",
                    true,
                    "https://evil.invalid")
                .statusCode())
        .isEqualTo(403);
    assertThat(post(account, null, UUID.randomUUID(), "hello").statusCode()).isEqualTo(201);
    sessions.deleteById(account.getId());
    assertThat(post(account, null, UUID.randomUUID(), "hello").statusCode()).isEqualTo(401);
  }

  @Test
  void unicodeCodePointsAreCountedWithoutNormalizingOrInterpretingText() throws Exception {
    Session account = session("reader");
    String emoji = new String(Character.toChars(0x1f642));
    for (int size : new int[] {279, 280}) {
      assertThat(post(account, null, UUID.randomUUID(), emoji.repeat(size)).statusCode())
          .isEqualTo(201);
      clock.advanceSeconds(3);
    }
    assertThat(post(account, null, UUID.randomUUID(), emoji.repeat(281)).statusCode())
        .isEqualTo(400);
    for (String invalid : new String[] {"", "  ", "\u0000", "hello\u0001", "hello\u0085"})
      assertThat(post(account, null, UUID.randomUUID(), invalid).statusCode()).isEqualTo(400);
    String exact = "<script>alert('x')</script> e\u0301";
    assertThat(post(account, null, UUID.randomUUID(), exact).statusCode()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject("SELECT body FROM chat_message WHERE body=?", String.class, exact))
        .isEqualTo(exact);
    assertThat(
            request(
                    account,
                    null,
                    UUID.randomUUID(),
                    "{\"text\":\"spoof\",\"authorId\":\"" + UUID.randomUUID() + "\"}",
                    true,
                    ORIGIN)
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void authoritativeThreeSecondBoundaryAppliesAcrossChannels() throws Exception {
    Session account = session("reader");
    assertThat(post(account, null, UUID.randomUUID(), "first").statusCode()).isEqualTo(201);
    UUID event = event(true);
    clock.advanceMillis(2999);
    HttpResponse<String> limited = post(account, event, UUID.randomUUID(), "second");
    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(limited.headers().firstValue("Retry-After")).contains("1");
    assertThat(limited.body()).contains("retryAt");
    clock.advanceMillis(1);
    assertThat(post(account, event, UUID.randomUUID(), "second").statusCode()).isEqualTo(201);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void acceptedRetryReturnsExactAckAfterEligibilityChangesAndConflictingReuseFails()
      throws Exception {
    Session account = session("reader");
    UUID key = UUID.randomUUID();
    HttpResponse<String> accepted = post(account, null, key, "ack lost");
    assertThat(accepted.statusCode()).isEqualTo(201);
    jdbc.update("UPDATE ufu_enrollment SET status='LIBERADO' WHERE identity_id=?", author);
    assertThat(post(account, null, key, "ack lost").body()).isEqualTo(accepted.body());
    assertThat(post(account, null, key, "changed").statusCode()).isEqualTo(409);
    assertThat(post(account, event(true), key, "ack lost").statusCode()).isEqualTo(409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void unknownDraftAndGlobalAliasAreNotWritable() throws Exception {
    Session account = session("reader");
    for (UUID event : new UUID[] {UUID.randomUUID(), event(false), global})
      assertThat(post(account, event, UUID.randomUUID(), "refused").statusCode()).isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isZero();
  }

  @Test
  void acceptedWriteRenewsIdleActivityAndTriggersCommittedPush() throws Exception {
    Session account = session("reader");
    Instant before = sessions.findById(account.getId()).getLastAccessedTime();
    try (Wire wire = connected(account)) {
      wire.subscribe("/chat/global", "0");
      wire.message();
      clock.advanceSeconds(1);
      assertThat(post(account, null, UUID.randomUUID(), "push").statusCode()).isEqualTo(201);
      assertThat(wire.message()).contains("resyncRequired").doesNotContain("push");
      assertThat(sessions.findById(account.getId()).getLastAccessedTime()).isAfter(before);
    }
  }

  @Test
  void receiptFactsRejectUpdateDeleteAndRuntimeTruncate() throws Exception {
    assertThat(post(session("reader"), null, UUID.randomUUID(), "history").statusCode())
        .isEqualTo(201);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE chat_write_receipt SET idempotency_key=idempotency_key"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM chat_write_receipt"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    try (var connection =
            java.sql.DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      sql.execute("CREATE ROLE chat_write_runtime NOLOGIN NOSUPERUSER");
      sql.execute("GRANT USAGE ON SCHEMA public TO chat_write_runtime");
      sql.execute("SET betufu.runtime_role = 'chat_write_runtime'");
      String migration;
      try (var resource = getClass().getResourceAsStream("/db/migration/V40__chat_writing.sql")) {
        migration = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
      // Run the exact migration grant block for a distinct non-superuser role.
      sql.execute(migration.substring(migration.indexOf("DO $$")));
      sql.execute("SET ROLE chat_write_runtime");
      assertThatThrownBy(() -> sql.execute("TRUNCATE chat_write_receipt"))
          .isInstanceOf(java.sql.SQLException.class);
      assertThatThrownBy(
              () -> sql.execute("UPDATE chat_write_receipt SET idempotency_key=idempotency_key"))
          .isInstanceOf(java.sql.SQLException.class);
      assertThat(sql.execute("SELECT * FROM chat_write_receipt")).isTrue();
    }
  }
}

@Import(ChatWriteFixture.ClockConfiguration.class)
abstract class ChatWriteFixture extends ChatPushFixture {
  @Autowired MutableClock clock;
  @Autowired ChatWriteService writes;
  final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void eligible() {
    clock.current = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    enroll();
  }

  void enroll() {
    jdbc.update(
        "INSERT INTO ufu_enrollment(identity_id,matricula_hmac,hmac_version,status) VALUES (?,decode(repeat('ab',32),'hex'),1,'ATIVO')",
        author);
  }

  HttpResponse<String> post(Session account, UUID event, UUID key, String text) throws Exception {
    return request(
        account, event, key, json.writeValueAsString(java.util.Map.of("text", text)), true, ORIGIN);
  }

  HttpResponse<String> request(
      Session account, UUID event, UUID key, String body, boolean csrf, String origin)
      throws Exception {
    String cookie = "XSRF-TOKEN=" + TOKEN;
    if (account != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder()
                  .encodeToString(
                      account.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String target = event == null ? "global" : "events/" + event;
    var builder =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/chat/" + target + "/messages"))
            .header("Cookie", cookie)
            .header("Origin", origin)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (csrf) builder.header("X-XSRF-TOKEN", TOKEN);
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfiguration {
    @Bean
    @Primary
    MutableClock chatTestClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant current = Instant.now();

    void advanceSeconds(long seconds) {
      current = current.plusSeconds(seconds);
    }

    void advanceMillis(long millis) {
      current = current.plusMillis(millis);
    }

    @Override
    public Instant instant() {
      return current;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
