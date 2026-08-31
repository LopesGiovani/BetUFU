package br.com.betufu.sports.internal.result;

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

public final class SportsResultModels {

    private SportsResultModels() {
    }

    public record SportsResultScoreRequest(
            @Min(1) @Max(32) int slotNo,
            @Min(0) int score) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsResultScoreItem(
            int slotNo,
            String displayLabel,
            int score,
            UUID teamId,
            String teamName) {
    }

    public record SportsResultInformRequest(
            @NotBlank @Size(max = 2048) String sourceReference,
            @NotNull @Size(min = 2, max = 2) List<@Valid SportsResultScoreRequest> scores) {

        public SportsResultInformRequest {
            scores = scores == null ? null : List.copyOf(scores);
        }
    }

    public record SportsResultReviewRequest(
            @NotBlank @Size(max = 2048) String sourceReference,
            @NotNull @Size(min = 2, max = 2) List<@Valid SportsResultScoreRequest> scores) {

        public SportsResultReviewRequest {
            scores = scores == null ? null : List.copyOf(scores);
        }
    }

    public record SportsResultConfirmRequest(
            @NotBlank @Size(max = 2048) String sourceReference,
            @NotNull @Size(min = 2, max = 2) List<@Valid SportsResultScoreRequest> scores) {

        public SportsResultConfirmRequest {
            scores = scores == null ? null : List.copyOf(scores);
        }
    }

    public record SportsResultCorrectRequest(
            @NotBlank @Size(max = 1024) String reason,
            @NotBlank @Size(max = 2048) String sourceReference,
            @NotNull @Size(min = 2, max = 2) List<@Valid SportsResultScoreRequest> scores) {

        public SportsResultCorrectRequest {
            scores = scores == null ? null : List.copyOf(scores);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsResultVersion(
            UUID versionId,
            UUID eventId,
            UUID competitionId,
            UUID editionId,
            long versionNo,
            String status,
            String sourceReference,
            String reason,
            List<SportsResultScoreItem> scores,
            UUID recordedByIdentityId,
            UUID confirmedByIdentityId,
            Instant recordedAt,
            Instant confirmedAt,
            UUID supersedesVersionId,
            long baseLockVersion) {

        public SportsResultVersion {
            scores = scores == null ? List.of() : List.copyOf(scores);
        }
    }
}
