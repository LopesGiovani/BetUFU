package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatReviewIT extends ChatSanctionFixture {
  @Test
  void privateReviewPendingKeepsPunishmentAndAnnulmentAppendsNewFacts() throws Exception {
    UUID occurrence = occurrence(),
        sanction = UUID.fromString(sanction(occurrence).get("sanctionId").asString());
    UUID key = UUID.randomUUID();
    var response = review(sanction, key);
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(review(sanction, key).body()).isEqualTo(response.body());
    assertThat(post(ownerSession, null, UUID.randomUUID(), "pending is still blocked").statusCode())
        .isEqualTo(403);
    var reviewId = UUID.fromString(json.readTree(response.body()).get("reviewId").asString());
    assertThat(call(adminSession, "GET", "/moderation/reviews", null, true).body())
        .contains(reviewId.toString());
    var decisionKey = UUID.randomUUID();
    var decision = decide(reviewId, "ANNUL", decisionKey);
    assertThat(decision.statusCode()).isEqualTo(200);
    assertThat(decide(reviewId, "ANNUL", decisionKey).body()).isEqualTo(decision.body());
    assertThat(post(ownerSession, null, UUID.randomUUID(), "writing restored").statusCode())
        .isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM chat_moderation_review_fact", Integer.class))
        .isEqualTo(2);
    assertThat(confirm(occurrence(), "CHAT_SUSPENSION_24H", UUID.randomUUID()).statusCode())
        .isEqualTo(201);
    assertThat(confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID()).statusCode())
        .isEqualTo(409);
    for (String table :
        java.util.List.of("chat_moderation_sanction_fact", "chat_moderation_review_fact")) {
      assertThatThrownBy(() -> jdbc.execute("DELETE FROM " + table))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(() -> jdbc.execute("UPDATE " + table + " SET reason=reason"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
  }

  @Test
  void inactiveOwnerCanReadAndReviewButOtherAccountsCannotInspectOrReview() throws Exception {
    UUID id = UUID.fromString(sanction(occurrence()).get("sanctionId").asString());
    jdbc.update("UPDATE ufu_enrollment SET status='LIBERADO' WHERE identity_id=?", author);
    assertThat(call(ownerSession, "GET", "/me/sanctions", null, true).statusCode()).isEqualTo(200);
    assertThat(review(id, UUID.randomUUID()).statusCode()).isEqualTo(201);
    participant("other-owner");
    var other = session("other-owner");
    assertThat(call(other, "GET", "/me/sanctions", null, true).body()).isEqualTo("[]");
    assertThat(
            call(
                    other,
                    "POST",
                    "/me/sanctions/" + id + "/reviews",
                    reviewBody(UUID.randomUUID()),
                    true)
                .statusCode())
        .isEqualTo(404);
    assertThat(call(ownerSession, "GET", "/moderation/reviews", null, true).statusCode())
        .isEqualTo(403);
  }

  @Test
  void maintainingIsAuditedAndConflictingDecisionDoesNotLiftOrAdvance() throws Exception {
    UUID id = UUID.fromString(sanction(occurrence()).get("sanctionId").asString());
    UUID review =
        UUID.fromString(
            json.readTree(review(id, UUID.randomUUID()).body()).get("reviewId").asString());
    UUID key = UUID.randomUUID();
    assertThat(decide(review, "MAINTAIN", key).statusCode()).isEqualTo(200);
    assertThat(decide(review, "ANNUL", key).statusCode()).isEqualTo(409);
    assertThat(post(ownerSession, null, UUID.randomUUID(), "still blocked").statusCode())
        .isEqualTo(403);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(1);
    assertThat(call(ownerSession, "GET", "/me/sanctions", null, true).body())
        .contains("MAINTAINED");
  }
}
