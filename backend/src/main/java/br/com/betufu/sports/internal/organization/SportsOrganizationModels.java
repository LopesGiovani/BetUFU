package br.com.betufu.sports.internal.organization;

import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Projections for permanent organizations and edition-scoped teams. */
public final class SportsOrganizationModels {

  private SportsOrganizationModels() {}

  public record SportsOrganizationSummary(
      UUID organizationId, String name, String slug, String status, long lockVersion) {}

  public record SportsTeamSummary(
      UUID teamId,
      UUID organizationId,
      String organizationName,
      UUID competitionId,
      UUID editionId,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String name,
      String slug,
      String status,
      long lockVersion) {}

  public record TeamCreateRequest(
      @NotNull UUID sportId,
      @NotNull UUID categoryId,
      @NotNull UUID organizationId,
      NameSlugRequest identity) {}

  public record OrganizationListResponse(List<SportsOrganizationSummary> organizations) {
    public OrganizationListResponse {
      organizations = organizations == null ? List.of() : List.copyOf(organizations);
    }
  }
}
