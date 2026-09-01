package br.com.betufu.sports.internal.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SportsEventModels {

  private SportsEventModels() {}

  public enum EventFormat {
    HEAD_TO_HEAD
  }

  public enum VenueState {
    LOCAL_A_DEFINIR,
    LOCAL_DEFINIDO
  }

  public record SportsEventSlotRequest(
      @Min(1) @Max(32) int slotNo, @NotBlank @Size(max = 160) String displayLabel, UUID teamId) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SportsEventSlotItem(
      int slotNo, String displayLabel, UUID teamId, String teamName) {}

  public record SportsEventCreateRequest(
      @NotBlank @Size(max = 200) String title,
      @NotNull EventFormat format,
      @NotNull Instant scheduledAt,
      @NotBlank @Size(max = 64) String zoneId,
      @NotNull VenueState venueState,
      @NotBlank @Size(max = 2048) String sourceReference,
      @NotNull @Size(min = 2, max = 2) List<@Valid SportsEventSlotRequest> slots,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      UUID venueId,
      UUID venueSpaceId) {

    public SportsEventCreateRequest {
      slots = slots == null ? null : List.copyOf(slots);
    }
  }

  public record SportsEventUpdateRequest(
      @NotBlank @Size(max = 200) String title,
      @NotNull EventFormat format,
      @NotNull Instant scheduledAt,
      @NotBlank @Size(max = 64) String zoneId,
      @NotNull VenueState venueState,
      @NotBlank @Size(max = 2048) String sourceReference,
      @NotNull @Size(min = 2, max = 2) List<@Valid SportsEventSlotRequest> slots,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      UUID venueId,
      UUID venueSpaceId) {

    public SportsEventUpdateRequest {
      slots = slots == null ? null : List.copyOf(slots);
    }
  }

  public record SportsEventSuspendRequest(@Size(max = 1024) String reason) {}

  public record SportsEventPostponeRequest(
      @NotNull Instant scheduledAt,
      @NotBlank @Size(max = 64) String zoneId,
      @Size(max = 1024) String reason) {}

  public record SportsEventCancelRequest(@NotBlank @Size(max = 1024) String reason) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SportsEventDetail(
      UUID eventId,
      UUID competitionId,
      String competitionName,
      UUID editionId,
      String editionName,
      String title,
      EventFormat format,
      String status,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      String sourceReference,
      long lockVersion,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      UUID phaseId,
      String phaseName,
      UUID venueId,
      String venueName,
      UUID venueSpaceId,
      String venueSpaceName,
      List<SportsEventSlotItem> slots,
      UUID confirmedVersionId,
      String statusReason,
      Instant createdAt,
      Instant updatedAt) {

    public SportsEventDetail {
      slots = slots == null ? List.of() : List.copyOf(slots);
    }
  }
}
