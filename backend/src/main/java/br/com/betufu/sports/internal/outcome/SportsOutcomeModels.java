package br.com.betufu.sports.internal.outcome;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SportsOutcomeModels {
  private SportsOutcomeModels() {}

  public enum OutcomeKind {
    ADMINISTRATIVE_WALKOVER,
    ABANDONED_NO_VALID_RESULT
  }

  public record AdministrativeOutcomeRequest(
      @NotNull OutcomeKind outcomeKind,
      @NotBlank @Size(max = 1024) String reason,
      @NotBlank @Size(max = 2048) String sourceReference,
      @Min(1) @Max(2) Integer winnerSlotNo) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OutcomeFact(
      UUID factId,
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      OutcomeKind outcomeKind,
      String sourceReference,
      String reason,
      UUID recordedByIdentityId,
      Instant sportTime,
      Instant knownAt,
      UUID supersedesFactId,
      Integer winnerSlotNo) {}
}
