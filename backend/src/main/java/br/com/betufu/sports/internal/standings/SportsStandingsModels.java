package br.com.betufu.sports.internal.standings;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SportsStandingsModels {

    private SportsStandingsModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsStandingsRow(
            @Min(1) int position,
            UUID teamId,
            @NotBlank @Size(max = 160) String participantName,
            @NotNull List<@Size(max = 64) String> values) {

        public SportsStandingsRow {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record SportsStandingsCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96) String slug,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            @NotNull List<@NotBlank @Size(max = 64) String> columns) {

        public SportsStandingsCreateRequest {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record SportsStandingsUpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 96) String slug,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            @NotNull List<@NotBlank @Size(max = 64) String> columns,
            @NotNull List<@Valid SportsStandingsRow> rows) {

        public SportsStandingsUpdateRequest {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record SportsStandingsPublishRequest(
            @NotBlank @Size(max = 2048) String sourceReference) {
    }

    public record SportsStandingsCorrectRequest(
            @NotBlank @Size(max = 1024) String reason,
            @NotBlank @Size(max = 2048) String sourceReference,
            List<@NotBlank @Size(max = 64) String> columns,
            List<@Valid SportsStandingsRow> rows) {

        public SportsStandingsCorrectRequest {
            columns = columns == null ? null : List.copyOf(columns);
            rows = rows == null ? null : List.copyOf(rows);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SportsStandingsSummary(
            UUID standingsId,
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
    public record SportsStandingsSnapshot(
            UUID standingsId,
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
            List<String> columns,
            List<SportsStandingsRow> rows) {

        public SportsStandingsSnapshot {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}
