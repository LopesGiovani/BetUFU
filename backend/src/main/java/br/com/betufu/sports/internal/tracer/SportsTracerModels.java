package br.com.betufu.sports.internal.tracer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class SportsTracerModels {

    private SportsTracerModels() {
    }

    public enum EventFormat {
        HEAD_TO_HEAD
    }

    public enum VenueState {
        LOCAL_A_DEFINIR
    }

    public record CompetitionCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96)
                    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug) {
    }

    public record CompetitionResponse(
            UUID competitionId,
            String name,
            String slug,
            String status,
            long lockVersion) {
    }

    public record EditionCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96)
                    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record EditionResponse(
            UUID editionId,
            UUID competitionId,
            String name,
            String slug,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            long lockVersion) {
    }

    public record EventSlotRequest(
            @Min(1) @Max(32) int slotNo,
            @NotBlank @Size(max = 160) String displayLabel) {
    }

    public record EventCreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull EventFormat format,
            @NotNull Instant scheduledAt,
            @NotBlank @Size(max = 64) String zoneId,
            @NotNull VenueState venueState,
            @NotBlank @Size(max = 2048) String sourceReference,
            @NotNull @Size(min = 2, max = 2) List<@Valid EventSlotRequest> slots) {

        public EventCreateRequest {
            slots = slots == null ? null : List.copyOf(slots);
        }
    }

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
            long lockVersion) {
    }

    public record PublishEventRequest(
            @NotNull @PositiveOrZero Long expectedLockVersion) {
    }

    public record PublicSlotResponse(int slotNo, String displayLabel) {
    }

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
            List<PublicSlotResponse> slots) {

        public PublicEventResponse {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }
}
