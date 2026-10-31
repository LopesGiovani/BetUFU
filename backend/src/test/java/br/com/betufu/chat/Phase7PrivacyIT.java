package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.session.Session;

class Phase7PrivacyIT extends ChatCommunityFixture {
  @Test
  void restrictedIdentityExportIncludesOnlyItsOwnersRulesPreference() throws Exception {
    UUID admin = participant("final-privacy-admin");
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,status) VALUES (?,'ADMINISTRADOR_GERAL','VIGENTE')",
        admin);
    participant("final-privacy-other");
    var account = session("reader");
    var administrator = session("final-privacy-admin");
    var other = session("final-privacy-other");
    assertThat(
            command(other, "POST", "/rules/preference/dismiss", null, null, true, ORIGIN)
                .statusCode())
        .isEqualTo(200);
    var requested =
        privacy(
            account,
            "POST",
            "/requests",
            "{\"subjectKind\":\"ACCOUNT\",\"subjectRef\":null,\"requestKind\":\"ACCESS\",\"proofReference\":null,\"verifiedChannel\":\"AUTHENTICATED_SESSION\"}");
    assertThat(requested.statusCode()).isEqualTo(201);
    String path =
        "/requests/" + json.readTree(requested.body()).get("caseId").asText() + "/exports";
    assertThat(privacy(account, "POST", path, null).statusCode()).isEqualTo(403);
    var initial = privacy(administrator, "POST", path, null);
    assertThat(initial.statusCode()).isEqualTo(201);
    var identity = json.readTree(initial.body()).get("data").get("IDENTITY").get(0).get("fields");
    assertThat(identity.has("chatRulesDismissed")).isTrue();
    assertThat(identity.get("chatRulesDismissed").asText()).isEqualTo("false");
    assertThat(identity.get("nickname").asText()).isEqualTo("push_reader");
    assertThat(
            command(account, "POST", "/rules/preference/dismiss", null, null, true, ORIGIN)
                .statusCode())
        .isEqualTo(200);
    var updated = privacy(administrator, "POST", path, null);
    assertThat(updated.statusCode()).isEqualTo(201);
    assertThat(
            json.readTree(updated.body())
                .get("data")
                .get("IDENTITY")
                .get(0)
                .get("fields")
                .get("chatRulesDismissed")
                .asText())
        .isEqualTo("true");
    assertThat(updated.body())
        .doesNotContain("final-privacy-other", "issuer", "subject", "matricula_hmac");
    jdbc.update("DELETE FROM betufu_profile WHERE identity_id=?", author);
    var withoutProfile = privacy(administrator, "POST", path, null);
    assertThat(withoutProfile.statusCode()).isEqualTo(201);
    assertThat(
            json.readTree(withoutProfile.body())
                .get("data")
                .get("IDENTITY")
                .get(0)
                .get("fields")
                .get("chatRulesDismissed")
                .asText())
        .isEqualTo("true");
    var download = privacy(account, "GET", path + "/latest", null);
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(privacy(other, "GET", path + "/latest", null).statusCode()).isEqualTo(403);
    assertThat(privacy(null, "GET", path + "/latest", null).statusCode()).isEqualTo(401);
    sessions.deleteById(account.getId());
    assertThat(privacy(account, "GET", path + "/latest", null).statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> privacy(Session actor, String method, String path, String body)
      throws Exception {
    String cookie = "XSRF-TOKEN=" + TOKEN;
    if (actor != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder().encodeToString(actor.getId().getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/privacy" + path))
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .header("Origin", ORIGIN)
            .header("X-XSRF-TOKEN", TOKEN)
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
