package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class RankingPrivacyIT extends RankingFixture {
  @Test
  void releasedEnrollmentPreservesRankButPublicProjectionHasNoPrivateFields() throws Exception {
    UUID person = identity("public-handle", true);
    decision(person, "WON");
    jdbc.update(
        "UPDATE public.ufu_enrollment SET status = 'LIBERADO', updated_at = CURRENT_TIMESTAMP(6) WHERE identity_id = ?",
        person);

    RankingModels.Entry entry = service.findLifetimeRanking().getFirst();
    assertThat(entry.position()).isEqualTo(1);
    assertThat(entry.wins()).isEqualTo(1);
    assertThat(entry.verificationActive()).isFalse();
    String publicJson = new ObjectMapper().writeValueAsString(entry);
    assertThat(publicJson)
        .contains("public-handle", "verificationActive")
        .doesNotContain(
            person.toString(), "identityId", "matricula", "hmac", "issuer", "subject", "LIBERADO");
    assertThat(service.findMemberMetrics(person).orElseThrow().verificationActive()).isFalse();
  }
}
