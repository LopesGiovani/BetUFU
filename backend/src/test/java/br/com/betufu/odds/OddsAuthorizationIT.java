package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.odds.internal.governance.OddsAuthorizationService;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsAuthorizationIT {

  @Test
  void revalidatesRoleAndPersistedScopeWithoutTrustingCapabilityPresence() {
    OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
    UUID identityId = UUID.randomUUID();
    UUID allowedScope = UUID.randomUUID();
    when(lookup.lookupIdentity("issuer", "admin"))
        .thenReturn(
            Optional.of(
                new OperationalIdentity(
                    identityId,
                    Set.of(new GrantedRole("ADMINISTRADOR_COMPETICAO", allowedScope)))));
    OddsAuthorizationService service = new OddsAuthorizationService(lookup);

    assertThat(
            service.isAuthorized(
                "issuer", "admin", OddsAuthorizationService.MANAGE_ODDS_MARKET, allowedScope))
        .isTrue();
    assertThat(
            service.isAuthorized(
                "issuer", "admin", OddsAuthorizationService.MANAGE_ODDS_MARKET, UUID.randomUUID()))
        .isFalse();
    assertThat(service.isAuthorized("issuer", "admin", "MANAGE_ODDS_MARKET", null)).isFalse();
    assertThat(service.isAuthorized("issuer", "admin", "UNKNOWN", allowedScope)).isFalse();
    assertThat(
            service.isAuthorized(
                "issuer", "missing", OddsAuthorizationService.VIEW_ODDS_MARKET, allowedScope))
        .isFalse();
    assertThatThrownBy(
            () ->
                service.requireAuthorized(
                    "issuer",
                    "admin",
                    OddsAuthorizationService.MANAGE_ODDS_MARKET,
                    UUID.randomUUID()))
        .isInstanceOf(OddsAuthorizationService.OddsAuthorizationException.class)
        .hasMessage("ROLE_OR_SCOPE_NOT_AUTHORIZED");
  }
}
