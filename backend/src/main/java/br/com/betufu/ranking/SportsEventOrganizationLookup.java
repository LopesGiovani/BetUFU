package br.com.betufu.ranking;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned event boundary for achievement evaluation. */
public interface SportsEventOrganizationLookup {
  record EventOrganizations(
      UUID editionId, UUID phaseId, UUID homeOrganizationId, UUID awayOrganizationId) {
    public UUID organizationFor(String selection) {
      return switch (selection) {
        case "HOME" -> homeOrganizationId;
        case "AWAY" -> awayOrganizationId;
        default -> null;
      };
    }
  }

  Optional<EventOrganizations> findEventOrganizations(UUID eventId);
}
