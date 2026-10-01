package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.settlement.SettlementModels.SettlementReason;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementContractTest {
  @Test
  void roundsMoneyHalfUpAndRejectsUnscaledFacts() {
    assertThat(SettlementModels.monetaryAmount(new BigDecimal("10.005")))
        .isEqualTo(new BigDecimal("10.01"));
    assertThat(SettlementModels.monetaryAmount(new BigDecimal("10.004")))
        .isEqualTo(new BigDecimal("10.00"));
    assertThatThrownBy(
            () ->
                new SettlementModels.Unit(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    SettlementState.WON,
                    SettlementReason.CONFIRMED_RESULT,
                    new BigDecimal("10.005"),
                    null,
                    "key",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void publicSnapshotsContainNoPrivateOrForeignInternalTypes() {
    assertThat(
            Arrays.stream(SportsResultForSettlementPort.ResultSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactlyInAnyOrder(
            "eventId",
            "outcomeFactId",
            "confirmedVersionId",
            "kind",
            "homeScore",
            "awayScore",
            "winnerSlotNo",
            "confirmedAt",
            "knownAt");
    assertThat(
            Arrays.stream(
                    PredictionForSettlementPort.PredictionSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactlyInAnyOrder(
            "predictionId",
            "identityId",
            "eventId",
            "marketId",
            "state",
            "selection",
            "stake",
            "acceptedOdds",
            "potentialReturn",
            "ruleVersion",
            "acceptedAt");
    for (RecordComponent component :
        PredictionForSettlementPort.PredictionSnapshot.class.getRecordComponents()) {
      assertThat(component.getType().getName()).doesNotContain(".internal.");
      assertThat(component.getName()).doesNotContain("email", "enrollment", "hmac");
    }
    assertThat(SettlementState.values())
        .extracting(Enum::name)
        .containsExactly("PENDING", "WON", "LOST", "REFUNDED", "ANNULLED");
  }
}
