package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.settlement.SettlementModels.Authorization;
import br.com.betufu.settlement.SettlementModels.SettlementCase;
import br.com.betufu.settlement.internal.SettlementRecoveryJob;
import br.com.betufu.settlement.internal.SettlementService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementRecoveryIT extends SettlementFixture {
  @Test
  void failureAfterFirstUnitResumesWithoutDuplicateCredit() {
    addPrediction("HOME");
    AtomicInteger calls = new AtomicInteger();
    SettlementCreditPort interrupted =
        new SettlementCreditPort() {
          @Override
          public UUID payout(UUID identityId, BigDecimal amount, UUID settlementId) {
            if (calls.incrementAndGet() == 2)
              throw new IllegalStateException("simulated interruption");
            return credits.payout(identityId, amount, settlementId);
          }

          @Override
          public UUID refund(UUID identityId, BigDecimal amount, UUID settlementId) {
            return credits.refund(identityId, amount, settlementId);
          }
        };
    SettlementService failing =
        settlementService(interrupted, Clock.systemUTC(), Duration.ofMinutes(2));
    UUID authorization = failing.authorize(eventId, administratorId, "auth-recovery");
    assertThatThrownBy(() -> failing.process(authorization))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("simulated interruption");
    UUID caseId =
        jdbc.queryForObject(
            "SELECT case_id FROM public.settlement_case WHERE authorization_id = ?",
            UUID.class,
            authorization);
    SettlementReadPort.CaseProgress partial = service.findCase(caseId).orElseThrow();
    assertThat(partial.status()).isEqualTo(SettlementReadPort.ProcessingStatus.FAILED);
    assertThat(partial.completedUnits()).isEqualTo(1);
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);

    new SettlementRecoveryJob(repository, service).recover();
    assertThat(service.findCase(caseId).orElseThrow().status())
        .isEqualTo(SettlementReadPort.ProcessingStatus.COMPLETE);
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void expiredLeaseIsClaimedWithControlledClock() {
    Instant start = Instant.parse("2026-10-01T12:00:00Z");
    Clock firstClock = Clock.fixed(start, ZoneOffset.UTC);
    SettlementService first = settlementService(credits, firstClock, Duration.ofSeconds(5));
    UUID authorization = first.authorize(eventId, administratorId, "auth-lease");
    Authorization approved = repository.findAuthorization(authorization).orElseThrow();
    UUID caseId = UUID.randomUUID();
    repository.insertCase(
        new SettlementCase(
            caseId,
            authorization,
            eventId,
            approved.outcomeFactId(),
            1,
            "h2h-settlement-v1",
            start));
    repository.insertProcessing(caseId, 1);
    assertThat(repository.claim(caseId, UUID.randomUUID(), start, start.plusSeconds(5))).isTrue();
    assertThat(first.findCase(caseId).orElseThrow().status())
        .isEqualTo(SettlementReadPort.ProcessingStatus.RUNNING);
    Clock beforeExpiry = Clock.fixed(start.plusSeconds(3), ZoneOffset.UTC);
    new SettlementRecoveryJob(
            repository,
            settlementService(credits, beforeExpiry, Duration.ofSeconds(5)),
            beforeExpiry)
        .recover();
    assertThat(ledgerEntries("PAYOUT")).isZero();
    Clock recoveredClock = Clock.fixed(start.plusSeconds(10), ZoneOffset.UTC);
    SettlementService recovered = settlementService(credits, recoveredClock, Duration.ofSeconds(5));
    new SettlementRecoveryJob(repository, recovered, recoveredClock).recover();
    assertThat(recovered.findCase(caseId).orElseThrow().status())
        .isEqualTo(SettlementReadPort.ProcessingStatus.COMPLETE);
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
  }
}
