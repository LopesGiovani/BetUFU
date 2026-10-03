package br.com.betufu.phase6;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.ranking.internal.AchievementController;
import br.com.betufu.ranking.internal.GroupInvitationService;
import br.com.betufu.ranking.internal.GroupRepository;
import br.com.betufu.ranking.internal.GroupService;
import br.com.betufu.ranking.internal.RankingController;
import br.com.betufu.settlement.internal.SettlementController;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class Phase6PrivacyIT extends Phase6Fixture {
  @Test
  void publicRankingAndOwnerHttpDtosNeverSerializeIdentityOrEnrollmentSecrets() {
    service.process(authorize("privacy-win"));
    evaluateAchievements();
    assertThat(achievements.findByIdentity(bettorId)).hasSize(1);
    var user =
        new DefaultOidcUser(
            Set.of(),
            OidcIdToken.withTokenValue("test-only")
                .issuer("https://idp.invalid")
                .subject(bettorId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    var mapper = JsonMapper.builder().build();
    String rankingJson =
        mapper.writeValueAsString(new RankingController(ranking).lifetime().getBody());
    String historyJson =
        mapper.writeValueAsString(
            new SettlementController(service, service, identities).history(user).getBody());
    String achievementJson =
        mapper.writeValueAsString(
            new AchievementController(achievements, identities).mine(user).getBody());
    for (String json : java.util.List.of(rankingJson, historyJson, achievementJson)) {
      assertThat(json)
          .doesNotContain(
              bettorId.toString(),
              administratorId.toString(),
              "issuer",
              "subject",
              "matricula",
              "hmac",
              "recordedBy",
              "authorizedBy",
              "idp.invalid",
              "source.invalid");
    }
    assertThat(
            mapper.readTree(rankingJson).get("entries").get(0).properties().stream()
                .map(java.util.Map.Entry::getKey))
        .containsExactlyInAnyOrder(
            "position", "nickname", "wins", "losses", "hitRate", "verificationActive");
    assertThat(service.findHistoryByIdentity(administratorId)).isEmpty();
  }

  @Test
  void nonmemberCannotSeeRosterOrMetricsAndGroupNeverMovesCredits() {
    GroupService groups =
        new GroupService(
            new GroupRepository(jdbc),
            new GroupInvitationService(),
            ranking,
            identities,
            manager,
            Clock.systemUTC());
    UUID outsider = createIdentity();
    long entries =
        jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class);
    UUID group =
        groups.create("https://idp.invalid", bettorId.toString(), "Private certification group");
    assertThat(groups.findGroup("https://idp.invalid", outsider.toString(), group)).isEmpty();
    assertThat(groups.findGroup("https://idp.invalid", outsider.toString(), UUID.randomUUID()))
        .isEmpty();
    assertThat(groups.findMine("https://idp.invalid", outsider.toString())).isEmpty();
    var view = groups.findGroup("https://idp.invalid", bettorId.toString(), group).orElseThrow();
    assertThat(view.standings()).hasSize(1);
    assertThat(JsonMapper.builder().build().writeValueAsString(view))
        .doesNotContain(
            bettorId.toString(), "identityId", "issuer", "subject", "matricula", "hmac");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class))
        .isEqualTo(entries);
    assertFinancialIntegrity();
  }
}
