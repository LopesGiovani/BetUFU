package br.com.betufu.sports.internal.roster;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SportsRosterModels {

  private SportsRosterModels() {}

  public record SportsRosterMemberItem(
      UUID athleteId,
      String athleteName,
      String athleteSlug,
      Integer jerseyNumber,
      String positionName,
      LocalDate affiliationStartDate,
      LocalDate affiliationEndDate) {}

  public record SportsRosterMemberRequest(
      @NotNull UUID athleteId,
      @Min(1) @Max(99) Integer jerseyNumber,
      @Size(max = 64) String positionName,
      LocalDate affiliationStartDate,
      LocalDate affiliationEndDate) {}

  public record SportsRosterDraft(
      UUID rosterId,
      UUID teamId,
      String teamName,
      UUID organizationId,
      String organizationName,
      UUID competitionId,
      String competitionName,
      UUID editionId,
      String editionName,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String status,
      long lockVersion,
      List<SportsRosterMemberItem> members) {

    public SportsRosterDraft {
      members = members == null ? List.of() : List.copyOf(members);
    }
  }

  public record SportsRosterUpdateRequest(List<@Valid SportsRosterMemberRequest> members) {

    public SportsRosterUpdateRequest {
      members = members == null ? List.of() : List.copyOf(members);
    }
  }

  public record SportsRosterPublication(
      UUID publicationId,
      UUID rosterId,
      UUID teamId,
      long versionNo,
      String teamName,
      UUID organizationId,
      String organizationName,
      UUID competitionId,
      String competitionName,
      UUID editionId,
      String editionName,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String reason,
      Instant publishedAt,
      long baseLockVersion,
      List<SportsRosterMemberItem> members) {

    public SportsRosterPublication {
      members = members == null ? List.of() : List.copyOf(members);
    }
  }

  public record SportsRosterCorrectRequest(
      @NotBlank @Size(max = 1024) String reason, List<@Valid SportsRosterMemberRequest> members) {

    public SportsRosterCorrectRequest {
      members = members == null ? List.of() : List.copyOf(members);
    }
  }
}
