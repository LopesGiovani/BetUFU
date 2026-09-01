package br.com.betufu.sports.internal.tracer;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SportsTracerModels {

  private SportsTracerModels() {}

  public enum EventFormat {
    HEAD_TO_HEAD
  }

  public enum VenueState {
    LOCAL_A_DEFINIR,
    LOCAL_DEFINIDO
  }

  public record CompetitionCreateRequest(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 96) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug) {}

  public record CompetitionResponse(
      UUID competitionId, String name, String slug, String status, long lockVersion) {}

  public record EditionCreateRequest(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 96) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
      LocalDate startDate,
      LocalDate endDate) {}

  public record EditionResponse(
      UUID editionId,
      UUID competitionId,
      String name,
      String slug,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      long lockVersion) {}

  public record EventSlotRequest(
      @Min(1) @Max(32) int slotNo, @NotBlank @Size(max = 160) String displayLabel) {}

  public record EventCreateRequest(
      @NotBlank @Size(max = 200) String title,
      @NotNull EventFormat format,
      @NotNull Instant scheduledAt,
      @NotBlank @Size(max = 64) String zoneId,
      @NotNull VenueState venueState,
      @NotBlank @Size(max = 2048) String sourceReference,
      @NotNull @Size(min = 2, max = 2) List<@Valid EventSlotRequest> slots,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      UUID venueId,
      UUID venueSpaceId) {

    public EventCreateRequest {
      slots = slots == null ? null : List.copyOf(slots);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record EventResponse(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      String title,
      EventFormat format,
      String status,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      long lockVersion,
      @JsonInclude(JsonInclude.Include.NON_NULL) UUID sportId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String sportName,
      @JsonInclude(JsonInclude.Include.NON_NULL) UUID categoryId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String categoryName,
      @JsonInclude(JsonInclude.Include.NON_NULL) UUID phaseId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String phaseName,
      @JsonInclude(JsonInclude.Include.NON_NULL) UUID venueId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String venueName,
      @JsonInclude(JsonInclude.Include.NON_NULL) UUID venueSpaceId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String venueSpaceName) {}

  public record PublishEventRequest(@NotNull @PositiveOrZero Long expectedLockVersion) {}

  public record PublicSlotResponse(int slotNo, String displayLabel) {}

  /** Public agenda projection; publication internals never cross this boundary. */
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PublicEventResponse(
      UUID publicationId,
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String competitionName,
      String editionName,
      String title,
      EventFormat format,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      String sourceReference,
      Instant publishedAt,
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

    public PublicEventResponse {
      slots = slots == null ? List.of() : List.copyOf(slots);
    }
  }
}
