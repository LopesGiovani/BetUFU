package br.com.betufu.settlement.internal;

import br.com.betufu.ranking.AchievementSettlementSource;
import br.com.betufu.settlement.SettlementReadPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Maps the settlement read contract into the consumer-owned achievement boundary. */
@Component
@Profile("runtime | integrated")
public class AchievementSettlementAdapter implements AchievementSettlementSource {
  private final SettlementReadPort settlements;

  public AchievementSettlementAdapter(SettlementReadPort settlements) {
    this.settlements = settlements;
  }

  @Override
  public List<CurrentFact> findCurrentFacts() {
    return settlements.findCurrentFacts().stream()
        .map(
            fact ->
                new CurrentFact(
                    fact.settlementId(),
                    fact.predictionId(),
                    fact.identityId(),
                    fact.eventId(),
                    fact.marketId(),
                    fact.acceptedAt(),
                    Decision.valueOf(fact.state().name()),
                    fact.selection(),
                    fact.acceptedOdds()))
        .toList();
  }
}
