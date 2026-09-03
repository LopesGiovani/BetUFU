package br.com.betufu.sports.internal.publicview;

import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsSportSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SportsPublicViewModels {

  private SportsPublicViewModels() {}

  public enum EventFormat {
    HEAD_TO_HEAD,
    GENERIC
  }

  public enum VenueState {
    LOCAL_A_DEFINIR,
    LOCAL_DEFINIDO
  }

  public record PublicSlotResponse(int slotNo, String displayLabel) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicSportsEventSummary(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      String competitionName,
      String editionName,
      String title,
      EventFormat format,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      List<PublicSlotResponse> slots,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      UUID phaseId,
      String phaseName,
      UUID venueId,
      String venueName,
      UUID venueSpaceId,
      String venueSpaceName) {

    public PublicSportsEventSummary {
      slots = slots == null ? List.of() : List.copyOf(slots);
    }
  }

  public record PublicSportsEventPage(
      List<PublicSportsEventSummary> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext) {

    public PublicSportsEventPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  // --- P2: Competição e Edição ---

  public record PublicEditionSummary(
      UUID editionId,
      UUID competitionId,
      String name,
      String slug,
      LocalDate startDate,
      LocalDate endDate) {}

  public record PublicCategorySummary(UUID categoryId, UUID editionId, String name, String slug) {}

  public record PublicPhaseSummary(
      UUID phaseId, UUID categoryId, UUID editionId, String name, String slug) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicVenueSpaceSummary(
      UUID venueSpaceId, UUID venueId, String name, String notes) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicVenueSummary(
      UUID venueId,
      UUID competitionId,
      String name,
      String address,
      String mapUrl,
      String notes,
      List<PublicVenueSpaceSummary> spaces) {

    public PublicVenueSummary {
      spaces = spaces == null ? List.of() : List.copyOf(spaces);
    }
  }

  public record PublicTeamSummary(
      UUID teamId,
      UUID organizationId,
      String organizationName,
      UUID editionId,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String name,
      String slug) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicCompetitionDetail(
      UUID competitionId,
      String name,
      String slug,
      List<SportsSportSummary> sports,
      List<PublicEditionSummary> editions,
      List<PublicCategorySummary> categories,
      List<PublicPhaseSummary> phases,
      List<PublicVenueSummary> venues,
      List<PublicTeamSummary> teams,
      List<PublicSportsEventSummary> recentEvents) {

    public PublicCompetitionDetail {
      sports = sports == null ? List.of() : List.copyOf(sports);
      editions = editions == null ? List.of() : List.copyOf(editions);
      categories = categories == null ? List.of() : List.copyOf(categories);
      phases = phases == null ? List.of() : List.copyOf(phases);
      venues = venues == null ? List.of() : List.copyOf(venues);
      teams = teams == null ? List.of() : List.copyOf(teams);
      recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
    }
  }

  // --- P3: Evento ---

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicEventSlotItem(
      int slotNo, String displayLabel, UUID teamId, String teamName) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicResultScoreItem(int slotNo, int score, String displayLabel, UUID teamId) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicEventResultDetail(
      long versionNo, String status, Instant confirmedAt, List<PublicResultScoreItem> scores) {

    public PublicEventResultDetail {
      scores = scores == null ? List.of() : List.copyOf(scores);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicSourceHistoryItem(
      long versionNo,
      String sourceReference,
      String reason,
      Instant recordedAt,
      Instant confirmedAt) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicEventDetail(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String competitionName,
      String editionName,
      String title,
      EventFormat format,
      String status,
      String statusReason,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      String sourceReference,
      Instant publishedAt,
      List<PublicEventSlotItem> slots,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      UUID phaseId,
      String phaseName,
      UUID venueId,
      String venueName,
      String venueAddress,
      String venueMapUrl,
      String venueNotes,
      UUID venueSpaceId,
      String venueSpaceName,
      String venueSpaceNotes,
      PublicEventResultDetail result,
      List<PublicSourceHistoryItem> sourceHistory) {

    public PublicEventDetail {
      slots = slots == null ? List.of() : List.copyOf(slots);
      sourceHistory = sourceHistory == null ? List.of() : List.copyOf(sourceHistory);
    }
  }

  // --- P4: Organização Esportiva ---

  public record PublicOrganizationTeamItem(
      UUID teamId,
      UUID editionId,
      String editionName,
      UUID competitionId,
      String competitionName,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String name,
      String slug) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicOrganizationRosterMemberItem(
      UUID athleteId,
      String athleteName,
      String athleteSlug,
      UUID teamId,
      String teamName,
      UUID editionId,
      String editionName,
      String sportName,
      String categoryName,
      Integer jerseyNumber,
      String positionName) {}

  public record PublicOrganizationDetail(
      UUID organizationId,
      String name,
      String slug,
      List<PublicOrganizationTeamItem> teams,
      List<PublicOrganizationRosterMemberItem> rosterMembers,
      List<PublicSportsEventSummary> events) {

    public PublicOrganizationDetail {
      teams = teams == null ? List.of() : List.copyOf(teams);
      rosterMembers = rosterMembers == null ? List.of() : List.copyOf(rosterMembers);
      events = events == null ? List.of() : List.copyOf(events);
    }
  }

  // --- P5: Equipe ---

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicTeamRosterMemberItem(
      UUID athleteId,
      String athleteName,
      String athleteSlug,
      Integer jerseyNumber,
      String positionName) {}

  public record PublicTeamRosterDetail(
      long versionNo, Instant publishedAt, List<PublicTeamRosterMemberItem> members) {

    public PublicTeamRosterDetail {
      members = members == null ? List.of() : List.copyOf(members);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicTeamDetail(
      UUID teamId,
      UUID organizationId,
      String organizationName,
      UUID editionId,
      String editionName,
      UUID competitionId,
      String competitionName,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String name,
      String slug,
      PublicTeamRosterDetail roster,
      List<PublicSportsEventSummary> events) {

    public PublicTeamDetail {
      events = events == null ? List.of() : List.copyOf(events);
    }
  }

  // --- P6: Atleta ---

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicAthleteTeamParticipation(
      UUID teamId,
      String teamName,
      UUID organizationId,
      String organizationName,
      UUID editionId,
      String editionName,
      String sportName,
      String categoryName,
      Integer jerseyNumber,
      String positionName) {}

  public record PublicAthleteStats(int totalParticipations, int totalWins, int totalLosses) {}

  public record PublicAthleteDetail(
      UUID athleteId,
      String name,
      String slug,
      List<PublicAthleteTeamParticipation> teams,
      List<PublicSportsEventSummary> events,
      PublicAthleteStats stats) {

    public PublicAthleteDetail {
      teams = teams == null ? List.of() : List.copyOf(teams);
      events = events == null ? List.of() : List.copyOf(events);
    }
  }
}
