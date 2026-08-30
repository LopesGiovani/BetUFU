package br.com.betufu.sports.internal.athlete;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Projections and requests for global sports athletes. */
public final class SportsAthleteModels {

    private SportsAthleteModels() {
    }

    public record SportsAthleteSummary(
            UUID athleteId,
            String name,
            String slug,
            String status,
            long lockVersion) {
    }

    public record SportsAthleteCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") @Size(max = 96) String slug,
            String matricula) {
    }

    public record AthleteListResponse(List<SportsAthleteSummary> athletes) {
        public AthleteListResponse {
            athletes = athletes == null ? List.of() : List.copyOf(athletes);
        }
    }
}
