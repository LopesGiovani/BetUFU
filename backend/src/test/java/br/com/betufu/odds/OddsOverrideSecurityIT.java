package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.odds.internal.governance.OddsAuthorizationService;
import br.com.betufu.odds.internal.override.OddsOverrideService;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsOverrideSecurityIT {
  @Test
  void foreignScopeIsRejectedBeforeAnyOverrideWrite() {
    OddsMarketRepository repository = mock(OddsMarketRepository.class);
    SportsResultForOddsPort sports = mock(SportsResultForOddsPort.class);
    OddsAuthorizationService authorization = mock(OddsAuthorizationService.class);
    AuditRecorder audit = mock(AuditRecorder.class);
    UUID marketId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID competitionId = UUID.randomUUID();
    when(repository.findMarket(marketId))
        .thenReturn(
            Optional.of(new OddsMarketRecord(marketId, eventId, "H2H", null, 0, null, null)));
    when(sports.getEventForOdds(eventId))
        .thenReturn(
            Optional.of(
                new SportsEventForOdds(
                    eventId,
                    competitionId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.now(),
                    "UTC",
                    SportsEventForOdds.EventFormat.HEAD_TO_HEAD,
                    List.of(
                        new SportsEventForOdds.Slot(1, "A", UUID.randomUUID(), "A"),
                        new SportsEventForOdds.Slot(2, "B", UUID.randomUUID(), "B")))));
    when(authorization.authorize(
            "issuer", "subject", OddsAuthorizationService.PROPOSE_ODDS_OVERRIDE, competitionId))
        .thenReturn(
            OddsAuthorizationService.AuthorizationDecision.denied("ROLE_OR_SCOPE_NOT_AUTHORIZED"));
    OddsOverrideService service = new OddsOverrideService(repository, sports, authorization, audit);
    assertThatThrownBy(() -> service.propose("issuer", "subject", marketId, "key", null))
        .isInstanceOf(RuntimeException.class);
    verify(repository, never())
        .saveOverride(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
  }
}
