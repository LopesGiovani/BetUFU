package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class PrivateGroupMetricsIT extends PrivateGroupFixture {
  @Test
  void groupUsesExactRankingMetricsAndGroupRelativeCompetitionPositions() throws Exception {
    UUID owner = identity("owner", true);
    UUID tiedA = identity("tied-a", true);
    UUID tiedB = identity("tied-b", true);
    UUID unranked = identity("unranked", true);
    UUID outsider = identity("outsider", true);
    decision(outsider, "WON");
    decision(outsider, "WON");
    decision(outsider, "WON");
    decision(owner, "WON");
    decision(owner, "WON");
    decision(tiedA, "WON");
    decision(tiedA, "LOST");
    decision(tiedB, "WON");
    decision(tiedB, "LOST");

    UUID group = groups.create(ISSUER, owner.toString(), "Ranking privado");
    for (UUID person : List.of(tiedA, tiedB, unranked)) {
      groups.join(
          ISSUER,
          person.toString(),
          groups
              .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
              .code());
    }
    List<GroupQueryPort.Standing> standings =
        groups.findGroup(ISSUER, tiedA.toString(), group).orElseThrow().standings();
    assertThat(standings).extracting(GroupQueryPort.Standing::position).containsExactly(1, 2, 2, 0);
    assertThat(standings)
        .extracting(GroupQueryPort.Standing::nickname)
        .containsExactly("owner", "tied-a", "tied-b", "unranked");
    assertThat(standings.get(1).hitRate()).isEqualByComparingTo("0.500000");
    assertThat(standings.get(3).wins()).isZero();
    assertThat(service.rankMembers(Set.of(owner, tiedA, tiedB, unranked)))
        .extracting(row -> row.metrics().position())
        .containsExactly(1, 2, 2, 0);
    assertThat(service.findLifetimeRanking().getFirst().nickname()).isEqualTo("outsider");
    String json =
        new ObjectMapper()
            .writeValueAsString(groups.findGroup(ISSUER, owner.toString(), group).orElseThrow());
    assertThat(json)
        .doesNotContain(
            owner.toString(),
            tiedA.toString(),
            outsider.toString(),
            "identityId",
            "subject",
            "issuer",
            "matricula",
            "hmac");
    assertThat(groups.findGroup(ISSUER, outsider.toString(), group)).isEmpty();
  }
}
