package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsOperationalCapabilitiesIT {

  @Test
  void projectsOddsCapabilitiesByRoleAndPersistedCompetitionScope() {
    OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
    UUID identityId = UUID.randomUUID();
    UUID competitionId = UUID.randomUUID();
    when(lookup.lookupIdentity("issuer", "admin"))
        .thenReturn(
            Optional.of(
                new OperationalIdentity(
                    identityId,
                    Set.of(new GrantedRole("ADMINISTRADOR_COMPETICAO", competitionId)))));
    when(lookup.lookupIdentity("issuer", "moderator"))
        .thenReturn(
            Optional.of(
                new OperationalIdentity(
                    UUID.randomUUID(), Set.of(new GrantedRole("MODERADOR", competitionId)))));

    OperationalCapabilitiesService service = new OperationalCapabilitiesService(lookup);

    assertThat(service.getCapabilities("issuer", "admin", competitionId).capabilities())
        .containsExactly(
            OperationalCapability.VIEW_OPERATIONAL_PANEL,
            OperationalCapability.MANAGE_SPORTS_CATALOG,
            OperationalCapability.VIEW_ODDS_MARKET,
            OperationalCapability.MANAGE_ODDS_MARKET,
            OperationalCapability.PROPOSE_ODDS_OVERRIDE,
            OperationalCapability.APPROVE_ODDS_OVERRIDE);
    assertThat(service.getCapabilities("issuer", "moderator", competitionId).capabilities())
        .containsExactly(
            OperationalCapability.VIEW_OPERATIONAL_PANEL, OperationalCapability.VIEW_ODDS_MARKET);
    assertThat(service.getCapabilities("issuer", "admin", UUID.randomUUID()).capabilities())
        .isEmpty();
  }
}
