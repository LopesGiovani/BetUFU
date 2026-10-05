package br.com.betufu;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.chat.ChatEventPort;
import br.com.betufu.chat.ChatIdentityPort;
import br.com.betufu.identity.ProfileEnrollmentStatusLookup;
import br.com.betufu.ranking.AchievementQueryPort;
import br.com.betufu.ranking.AchievementSettlementSource;
import br.com.betufu.ranking.FavoriteOrganizationLookup;
import br.com.betufu.ranking.GroupQueryPort;
import br.com.betufu.ranking.PrivateGroupPort;
import br.com.betufu.ranking.RankingEnrollmentSource;
import br.com.betufu.ranking.RankingNicknameSource;
import br.com.betufu.ranking.RankingQueryPort;
import br.com.betufu.ranking.RankingSettlementSource;
import br.com.betufu.ranking.SportsEventOrganizationLookup;
import br.com.betufu.settlement.PredictionForSettlementPort;
import br.com.betufu.settlement.SportsResultForSettlementPort;
import br.com.betufu.sports.CompetitionScopeCatalog;
import br.com.betufu.sports.EnrollmentFingerprintPort;
import br.com.betufu.sports.PublicEventMarketLookup;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

class ModulithArchitectureTests {

  private static final List<String> EXPECTED_BUSINESS_MODULES =
      List.of(
          "identity",
          "verification",
          "sports",
          "odds",
          "betting",
          "ledger",
          "settlement",
          "ranking",
          "chat",
          "audit");

  @Test
  @DisplayName(
      "ApplicationModules.verify() valida todas as fronteiras declaradas no grafo de produção")
  void verifiesModulithBoundaries() {
    ApplicationModules modules = ApplicationModules.of(BetufuApplication.class);
    modules.verify();
  }

  @Test
  @DisplayName(
      "Modulith identifica exatamente os dez módulos de negócio mais o módulo técnico platform (11 no total)")
  void verifiesAllTenBusinessModulesAndPlatformArePresent() {
    ApplicationModules modules = ApplicationModules.of(BetufuApplication.class);

    Set<String> detectedModules =
        modules.stream()
            .map(module -> module.getIdentifier().toString())
            .collect(Collectors.toSet());

    assertThat(detectedModules).containsAll(EXPECTED_BUSINESS_MODULES).contains("platform");

    // Trava estrutural: 10 módulos de capacidade de negócio (ARCH-02) + 1 módulo de suporte técnico
    // (platform)
  }

  @Test
  @DisplayName(
      "Modulith preserva os contratos publicos de sports e as named interfaces declaradas de platform")
  void verifiesDeclaredModuleContracts() {
    ApplicationModules modules = ApplicationModules.of(BetufuApplication.class);
    ApplicationModule identity = modules.getModuleByName("identity").orElseThrow();
    ApplicationModule chat = modules.getModuleByName("chat").orElseThrow();
    ApplicationModule sports = modules.getModuleByName("sports").orElseThrow();
    ApplicationModule settlement = modules.getModuleByName("settlement").orElseThrow();
    ApplicationModule ranking = modules.getModuleByName("ranking").orElseThrow();
    ApplicationModule platform = modules.getModuleByName("platform").orElseThrow();

    assertThat(identity.isExposed(ProfileEnrollmentStatusLookup.class)).isTrue();
    assertThat(chat.isExposed(ChatEventPort.class)).isTrue();
    assertThat(chat.isExposed(ChatIdentityPort.class)).isTrue();
    assertThat(sports.isExposed(EnrollmentFingerprintPort.class)).isTrue();
    assertThat(sports.isExposed(PublicEventMarketLookup.class)).isTrue();
    assertThat(sports.isExposed(CompetitionScopeCatalog.class)).isTrue();
    assertThat(settlement.isExposed(SportsResultForSettlementPort.class)).isTrue();
    assertThat(settlement.isExposed(PredictionForSettlementPort.class)).isTrue();
    assertThat(ranking.isExposed(RankingQueryPort.class)).isTrue();
    assertThat(ranking.isExposed(AchievementQueryPort.class)).isTrue();
    assertThat(ranking.isExposed(AchievementSettlementSource.class)).isTrue();
    assertThat(ranking.isExposed(FavoriteOrganizationLookup.class)).isTrue();
    assertThat(ranking.isExposed(SportsEventOrganizationLookup.class)).isTrue();
    assertThat(ranking.isExposed(RankingSettlementSource.class)).isTrue();
    assertThat(ranking.isExposed(RankingEnrollmentSource.class)).isTrue();
    assertThat(ranking.isExposed(RankingNicknameSource.class)).isTrue();
    assertThat(ranking.isExposed(GroupQueryPort.class)).isTrue();
    assertThat(ranking.isExposed(PrivateGroupPort.class)).isTrue();

    Set<String> namedInterfaces =
        platform.getNamedInterfaces().stream()
            .map(NamedInterface::getName)
            .collect(Collectors.toSet());
    assertThat(namedInterfaces).contains("api", "identity", "operations");
  }
}
