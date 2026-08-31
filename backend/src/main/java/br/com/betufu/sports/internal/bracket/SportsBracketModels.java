package br.com.betufu.sports.internal.bracket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SportsBracketModels {

    private SportsBracketModels() {
    }

    public enum SportsBracketSourceType {
        DIRECT,
        PREDECESSOR_WINNER,
        PREDECESSOR_LOSER,
        BYE
    }

    public record SportsBracketCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96) String slug,
            UUID sportId,
            UUID categoryId,
            UUID phaseId) {
    }

    public record SportsBracketSlotConfigureRequest(
            @Min(1) @Max(2) int slotNo,
            @NotNull SportsBracketSourceType sourceType,
            UUID sourceMatchId,
            @Size(max = 160) String seedLabel) {
    }

    public record SportsBracketMatchConfigureRequest(
            UUID matchId,
            @Min(1) int roundNo,
            @Min(1) int matchNo,
            @NotBlank @Size(max = 160) String roundName,
            UUID eventId,
            boolean isThirdPlace,
            @NotNull @Size(min = 2, max = 2) List<@Valid SportsBracketSlotConfigureRequest> slots) {

        public SportsBracketMatchConfigureRequest {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    public record SportsBracketUpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96) String slug,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            @NotNull List<@Valid SportsBracketMatchConfigureRequest> matches) {

        public SportsBracketUpdateRequest {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    public record SportsBracketPublishRequest(
            @NotBlank @Size(max = 2048) String sourceReference) {
    }

    public record SportsBracketCorrectRequest(
            @NotBlank @Size(max = 1024) String reason,
            @NotBlank @Size(max = 2048) String sourceReference,
            List<@Valid SportsBracketMatchConfigureRequest> matches) {

        public SportsBracketCorrectRequest {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsBracketSummary(
            UUID bracketId,
            UUID competitionId,
            UUID editionId,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            String name,
            String slug,
            String status,
            long lockVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsBracketSlotView(
            int slotNo,
            SportsBracketSourceType sourceType,
            UUID sourceMatchId,
            String displayLabel,
            UUID teamId,
            String teamName,
            Integer score,
            Boolean isWinner) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsBracketMatchView(
            UUID matchId,
            int roundNo,
            int matchNo,
            String roundName,
            UUID eventId,
            String eventTitle,
            Instant scheduledAt,
            String zoneId,
            String venueName,
            String venueSpaceName,
            boolean isThirdPlace,
            String status,
            List<SportsBracketSlotView> slots,
            Integer winnerSlotNo) {

        public SportsBracketMatchView {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    public record SportsBracketRound(
            int roundNo,
            String roundName,
            List<SportsBracketMatchView> matches) {

        public SportsBracketRound {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsBracketView(
            UUID bracketId,
            UUID competitionId,
            UUID editionId,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            String name,
            String slug,
            String status,
            Long versionNo,
            String sourceReference,
            String competitionName,
            String editionName,
            String sportName,
            String categoryName,
            String phaseName,
            Instant publishedAt,
            long lockVersion,
            List<SportsBracketRound> rounds) {

        public SportsBracketView {
            rounds = rounds == null ? List.of() : List.copyOf(rounds);
        }
    }
}
