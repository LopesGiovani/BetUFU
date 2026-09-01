package br.com.betufu.sports.internal.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable projections and commands for the scoped sports catalog. */
public final class SportsCatalogModels {

  private SportsCatalogModels() {}

  public record SportsSportSummary(
      UUID sportId, String name, String slug, String status, long lockVersion) {}

  public record SportsCompetitionSummary(
      UUID competitionId,
      String name,
      String slug,
      String status,
      long lockVersion,
      UUID sportId,
      String sportName) {}

  public record SportsEditionSummary(
      UUID editionId,
      UUID competitionId,
      String name,
      String slug,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      long lockVersion) {}

  public record SportsCategorySummary(
      UUID categoryId, UUID editionId, String name, String slug, String status, long lockVersion) {}

  public record SportsPhaseSummary(
      UUID phaseId,
      UUID editionId,
      UUID categoryId,
      String name,
      String slug,
      String status,
      long lockVersion) {}

  public record SportsVenueSpaceSummary(
      UUID venueSpaceId, UUID venueId, String name, String notes, long lockVersion) {}

  public record SportsVenueSummary(
      UUID venueId,
      UUID competitionId,
      String name,
      String address,
      String mapUrl,
      String notes,
      long lockVersion,
      List<SportsVenueSpaceSummary> spaces) {

    public SportsVenueSummary {
      spaces = spaces == null ? List.of() : List.copyOf(spaces);
    }
  }

  /** O1 response: only competition records authorized by the server are exposed. */
  public record SportsCatalogScope(
      List<SportsCompetitionSummary> competitions, boolean bootstrapAllowed) {

    public SportsCatalogScope {
      competitions = competitions == null ? List.of() : List.copyOf(competitions);
    }
  }

  /** O2 aggregate projection; all children are already scoped to the selected competition. */
  public record SportsCatalogTree(
      SportsCompetitionSummary competition,
      List<SportsSportSummary> sports,
      List<SportsEditionSummary> editions,
      List<SportsCategorySummary> categories,
      List<SportsPhaseSummary> phases,
      List<SportsVenueSummary> venues) {

    public SportsCatalogTree {
      sports = sports == null ? List.of() : List.copyOf(sports);
      editions = editions == null ? List.of() : List.copyOf(editions);
      categories = categories == null ? List.of() : List.copyOf(categories);
      phases = phases == null ? List.of() : List.copyOf(phases);
      venues = venues == null ? List.of() : List.copyOf(venues);
    }
  }

  public record NameSlugRequest(
      @NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 96) String slug) {}

  public record EditionRequest(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 96) String slug,
      LocalDate startDate,
      LocalDate endDate,
      Long expectedLockVersion) {}

  public record VenueRequest(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 320) String address,
      @Size(max = 2048) String mapUrl,
      @Size(max = 2000) String notes,
      Long expectedLockVersion) {}

  public record VenueSpaceRequest(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 1000) String notes,
      Long expectedLockVersion) {}
}
