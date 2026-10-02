package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.ranking.AchievementSettlementSource.CurrentFact;
import br.com.betufu.ranking.AchievementSettlementSource.Decision;
import br.com.betufu.ranking.SportsEventOrganizationLookup.EventOrganizations;
import br.com.betufu.ranking.internal.AchievementRepository;
import br.com.betufu.ranking.internal.AchievementRuleCatalog;
import br.com.betufu.ranking.internal.AchievementService;
import br.com.betufu.settlement.SettlementReadPort;
import br.com.betufu.settlement.internal.AchievementSettlementAdapter;
import br.com.betufu.settlement.internal.SettlementRepository;
import br.com.betufu.sports.internal.event.SportsEventOrganizationLookupService;
import br.com.betufu.verification.internal.RankingEnrollmentAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AchievementIT extends AchievementFixture {
  @Test
  void v38CatalogAndStreakProduceAnExplainableAwardWithoutFinancialEffects() {
    UUID person = identity("streak", true);
    decision(person, "WON");
    decision(person, "WON");
    decision(person, "WON");

    List<AchievementQueryPort.Achievement> awards = achievements.findByIdentity(person);
    assertThat(awards).hasSize(1);
    assertThat(awards.getFirst().code()).isEqualTo("STREAK_3");
    assertThat(awards.getFirst().state()).isEqualTo(AchievementQueryPort.AwardState.AWARDED);
    assertThat(awards.getFirst().name()).isNotBlank();
    assertThat(awards.getFirst().description()).isNotBlank();
    assertThat(awards.getFirst().rule()).isNotBlank();
    assertThat(awards.getFirst().catalogEdition()).isEqualTo("v1");
    assertThat(awards.getFirst().sportsEditionId()).isNotNull();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.achievement_award SET origin_key = 'tampered' WHERE award_id = ?",
                    awards.getFirst().awardId()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM public.achievement_award WHERE award_id = ?",
                    awards.getFirst().awardId()))
        .isInstanceOf(DataAccessException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class))
        .isZero();
  }

  @Test
  void catalogCoversUnderdogRoundAndFavoriteOrganization() {
    UUID person = UUID.randomUUID();
    UUID edition = UUID.randomUUID();
    UUID phase = UUID.randomUUID();
    UUID favorite = UUID.randomUUID();
    UUID firstEvent = UUID.randomUUID();
    UUID secondEvent = UUID.randomUUID();
    UUID thirdEvent = UUID.randomUUID();
    List<CurrentFact> facts =
        List.of(
            fact(person, firstEvent, "2.12"),
            fact(person, secondEvent, "3.25"),
            fact(person, thirdEvent, "2.12"));
    Map<UUID, EventOrganizations> contexts =
        Map.of(
            firstEvent, new EventOrganizations(edition, phase, favorite, null),
            secondEvent, new EventOrganizations(edition, phase, favorite, null),
            thirdEvent, new EventOrganizations(edition, phase, favorite, null));

    assertThat(
            AchievementRuleCatalog.evaluate(person, facts, contexts, Optional.of(favorite), true))
        .extracting(AchievementRuleCatalog.Candidate::code)
        .contains("STREAK_3", "UNDERDOG_3", "ROUND_3", "FAVORITE");
  }

  private static CurrentFact fact(UUID person, UUID event, String odds) {
    return new CurrentFact(
        UUID.randomUUID(),
        UUID.randomUUID(),
        person,
        event,
        UUID.randomUUID(),
        Instant.now(),
        Decision.WON,
        "HOME",
        new BigDecimal(odds));
  }
}

abstract class AchievementFixture extends RankingFixture {
  protected AchievementService achievements;

  @BeforeEach
  void setUpAchievements() {
    SettlementRepository settlementRepository = new SettlementRepository(jdbc);
    SettlementReadPort settlementFacts =
        new SettlementReadPort() {
          @Override
          public Optional<CaseProgress> findCase(UUID caseId) {
            return Optional.empty();
          }

          @Override
          public Optional<PredictionOutcome> findLatestByPrediction(UUID predictionId) {
            return Optional.empty();
          }

          @Override
          public List<SettlementReadPort.CurrentFact> findCurrentFacts() {
            return settlementRepository.findCurrentFacts();
          }
        };
    FavoriteOrganizationLookup favorite =
        person ->
            jdbc.query(
                "SELECT favorite_organization_id FROM public.betufu_profile WHERE identity_id = ?",
                rs ->
                    rs.next()
                        ? Optional.ofNullable(rs.getObject("favorite_organization_id", UUID.class))
                        : Optional.empty(),
                person);
    SportsEventOrganizationLookup sports = new SportsEventOrganizationLookupService(jdbc);
    achievements =
        new AchievementService(
            new AchievementSettlementAdapter(settlementFacts),
            service,
            new RankingEnrollmentAdapter(jdbc),
            favorite,
            sports,
            new AchievementRepository(jdbc));
  }

  @Override
  protected void insertUnit(
      UUID caseId, UUID prediction, UUID person, String state, UUID supersedes) {
    super.insertUnit(caseId, prediction, person, state, supersedes);
    UUID settlementId =
        jdbc.queryForObject(
            "SELECT settlement_id FROM public.prediction_settlement WHERE case_id = ? AND prediction_id = ?",
            UUID.class,
            caseId,
            prediction);
    jdbc.update(
        """
            INSERT INTO public.settlement_calculation
              (settlement_id, selection, stake, accepted_odds, potential_return,
               outcome_kind, winner_slot_no)
            SELECT ?, selection, stake, accepted_odds, potential_return,
                   'ADMINISTRATIVE_WALKOVER', 1
            FROM public.prediction WHERE prediction_id = ?
            """,
        settlementId,
        prediction);
  }
}
