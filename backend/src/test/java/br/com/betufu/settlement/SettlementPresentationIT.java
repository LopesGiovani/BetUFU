package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementPresentationIT extends SettlementFixture {
  @Test
  void historyUsesProducerPortsAndNeverReadsAnotherOwner() {
    assertThat(service.findHistoryByIdentity(administratorId)).isEmpty();
    var pending = service.findHistoryByIdentity(bettorId);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().state()).isEqualTo(SettlementState.PENDING);
    assertThat(pending.getFirst().amount()).isNull();
    service.process(service.authorize(eventId, administratorId, "history-current"));
    var settled = service.findHistoryByIdentity(bettorId).getFirst();
    assertThat(settled.current()).isTrue();
    assertThat(settled.processingStatus()).isEqualTo("COMPLETE");
    assertThat(settled.state()).isEqualTo(SettlementState.WON);
  }

  @Test
  void incompleteCorrectionPreservesOldFactButDoesNotPresentItAsCurrent() {
    service.process(service.authorize(eventId, administratorId, "history-old"));
    cancelEvent();
    var result = sports.findCurrent(eventId).orElseThrow();
    UUID authorization =
        service.authorize(eventId, result.outcomeFactId(), null, administratorId, "history-new");
    UUID caseId = UUID.randomUUID();
    repository.insertCase(
        new SettlementModels.SettlementCase(
            caseId,
            authorization,
            eventId,
            result.outcomeFactId(),
            2,
            "h2h-settlement-v1",
            java.time.Instant.now()));
    repository.insertProcessing(caseId, 1);
    var history = service.findHistoryByIdentity(bettorId);
    assertThat(history).hasSize(2);
    assertThat(history.getFirst().state()).isEqualTo(SettlementState.PENDING);
    assertThat(history.getFirst().current()).isTrue();
    assertThat(history.getFirst().amount()).isNull();
    assertThat(history.get(1).state()).isEqualTo(SettlementState.WON);
    assertThat(history.get(1).current()).isFalse();
  }

  @Test
  void exactFactAndExactNullableVersionAreBothCheckedUnderLock() {
    assertThatThrownBy(
            () ->
                service.authorize(eventId, resultFactId, null, administratorId, "missing-version"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                service.authorize(
                    eventId, UUID.randomUUID(), resultVersionId, administratorId, "bad-fact"))
        .isInstanceOf(IllegalStateException.class);
    cancelEvent();
    assertThatThrownBy(
            () -> service.authorize(eventId, resultFactId, null, administratorId, "stale-cancel"))
        .isInstanceOf(IllegalStateException.class);
    var cancellation = sports.findCurrent(eventId).orElseThrow();
    UUID authorization =
        service.authorize(
            eventId, cancellation.outcomeFactId(), null, administratorId, "exact-cancel");
    assertThat(authorization).isNotNull();
    assertThat(ledgerEntries("PAYOUT")).isZero();
  }

  @Test
  void discoverableCandidatesReuseCurrentSportingEligibility() {
    var candidates = service.findAuthorizationCandidates();
    assertThat(candidates).hasSize(1);
    assertThat(candidates.getFirst().outcomeFactId()).isEqualTo(resultFactId);
    assertThat(candidates.getFirst().homeScore()).isEqualTo(2);
    service.authorize(eventId, resultFactId, resultVersionId, administratorId, "candidate");
    assertThat(service.findAuthorizationCandidates().getFirst().alreadyAuthorized()).isTrue();
    jdbc.update("UPDATE public.sports_event SET status = 'SUSPENSO' WHERE event_id = ?", eventId);
    assertThat(service.findAuthorizationCandidates()).isEmpty();
  }
}
