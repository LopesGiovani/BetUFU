package br.com.betufu.odds.internal.override;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Administrative request and response records for exceptional price overrides. */
public final class OddsOverrideModels {
  private OddsOverrideModels() {}

  public record ProposeOverrideRequest(
      @NotNull UUID correctionCalculationId,
      @NotNull @DecimalMin(value = "0.01") BigDecimal previousOdds,
      @NotNull @DecimalMin(value = "0.01") BigDecimal proposedOdds,
      @NotBlank @Size(max = 256) String evidenceReference,
      @NotBlank @Size(max = 512) String reason,
      @NotNull Long expectedLockVersion) {}

  public record ApproveOverrideRequest(
      @NotNull Long expectedLockVersion, @NotBlank @Size(max = 512) String reason) {}

  public record OverrideView(
      UUID overrideId,
      UUID marketId,
      UUID calculationId,
      UUID proposerId,
      UUID approverId,
      String decision,
      BigDecimal previousOdds,
      BigDecimal proposedOdds,
      BigDecimal delta,
      Instant createdAt) {}
}
