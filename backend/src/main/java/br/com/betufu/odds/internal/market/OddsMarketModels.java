package br.com.betufu.odds.internal.market;

import br.com.betufu.sports.SportsRosterSnapshotForOdds;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs for the administrative market lifecycle. */
public final class OddsMarketModels {
  private OddsMarketModels() {}

  public record CreateMarketRequest(
      @NotNull UUID eventId,
      Boolean regulationAllowsDraw,
      Instant closeDeadline,
      BigDecimal margin,
      List<SportsRosterSnapshotForOdds> rosterSnapshots) {
    public CreateMarketRequest {
      regulationAllowsDraw = regulationAllowsDraw != null && regulationAllowsDraw;
      rosterSnapshots = rosterSnapshots == null ? List.of() : List.copyOf(rosterSnapshots);
    }
  }

  public record LifecycleRequest(
      @NotNull MarketTransition.Action action, Long expectedLockVersion, String reason) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MarketView(
      UUID marketId,
      UUID eventId,
      String marketType,
      MarketState state,
      UUID currentVersionId,
      long versionNo,
      Instant closeDeadline,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt) {}

  public record OddsMarketVersion(
      UUID marketVersionId,
      UUID marketId,
      long versionNo,
      MarketState state,
      Instant closeDeadline,
      UUID supersedesVersionId,
      Instant createdAt) {}
}
