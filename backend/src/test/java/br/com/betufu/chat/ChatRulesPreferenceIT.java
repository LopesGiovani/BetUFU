package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.chat.internal.privacy.ChatPrivacyAdapter;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.session.Session;

class ChatRulesPreferenceIT extends ChatCommunityFixture {
  @Autowired ChatPrivacyAdapter privacy;

  @Test
  void missingPreferenceIsFalseAndDismissalSynchronizesAcrossSessions() throws Exception {
    Session first = session("reader");
    Session second = session("reader");
    var initial = command(first, "GET", "/rules/preference", null, null, true, ORIGIN);
    assertThat(initial.statusCode()).isEqualTo(200);
    assertThat(initial.body()).isEqualTo("{\"dismissed\":false}");
    assertThat(initial.headers().firstValue("Cache-Control")).contains("private, no-store");

    var saved = command(first, "POST", "/rules/preference/dismiss", null, null, true, ORIGIN);
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(saved.body()).isEqualTo("{\"dismissed\":true}");
    assertThat(saved.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(command(second, "GET", "/rules/preference", null, null, true, ORIGIN).body())
        .isEqualTo("{\"dismissed\":true}");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_rules_dismissal", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void sessionAndCsrfAreRequiredAndResponseContainsOnlyTheBoolean() throws Exception {
    assertThat(command(null, "GET", "/rules/preference", null, null, true, ORIGIN).statusCode())
        .isEqualTo(401);
    Session actor = session("reader");
    HttpResponse<String> noCsrf =
        command(actor, "POST", "/rules/preference/dismiss", null, null, false, ORIGIN);
    assertThat(noCsrf.statusCode()).isEqualTo(403);
    assertThat(noCsrf.body()).doesNotContain(author.toString(), "accountId", "identityId");
  }

  @Test
  void concurrentRetriesKeepOneServerTimestampAndDoNotCrossAccounts() throws Exception {
    Session first = session("reader");
    Session second = session("reader");
    CompletableFuture<HttpResponse<String>> a =
        CompletableFuture.supplyAsync(() -> uncheckedDismiss(first));
    CompletableFuture<HttpResponse<String>> b =
        CompletableFuture.supplyAsync(() -> uncheckedDismiss(second));
    assertThat(a.get().statusCode()).isEqualTo(200);
    assertThat(b.get().statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_rules_dismissal WHERE owner_identity_id=?",
                Integer.class,
                author))
        .isEqualTo(1);
    UUID foreign = participant("other-rules-account");
    assertThat(
            command(
                    session("other-rules-account"),
                    "GET",
                    "/rules/preference",
                    null,
                    null,
                    true,
                    ORIGIN)
                .body())
        .isEqualTo("{\"dismissed\":false}");
    RowMapper<Instant> timestampMapper = (rs, row) -> rs.getTimestamp(1).toInstant();
    Instant timestamp =
        jdbc.queryForObject(
            "SELECT dismissed_at FROM chat_rules_dismissal WHERE owner_identity_id=?",
            timestampMapper,
            author);
    assertThat(uncheckedDismiss(first).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT dismissed_at FROM chat_rules_dismissal WHERE owner_identity_id=?",
                timestampMapper,
                author))
        .isEqualTo(timestamp);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_rules_dismissal WHERE owner_identity_id=?",
                Integer.class,
                foreign))
        .isZero();
  }

  @Test
  void privacyAdapterReadsOnlyThePersistedBooleanPreference() throws Exception {
    assertThat(privacy.rulesDismissed(author)).isFalse();
    assertThat(uncheckedDismiss(session("reader")).statusCode()).isEqualTo(200);
    assertThat(privacy.rulesDismissed(author)).isTrue();
    assertThat(privacy.rulesDismissed(participant("rules-other"))).isFalse();
  }

  private HttpResponse<String> uncheckedDismiss(Session actor) {
    try {
      return command(actor, "POST", "/rules/preference/dismiss", null, null, true, ORIGIN);
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }
}
