package br.com.betufu.ranking;

import java.math.BigDecimal;

public final class RankingModels {
  private RankingModels() {}

  /** Public response: no account identifier or private enrollment state. */
  public record Entry(
      int position,
      String nickname,
      long wins,
      long losses,
      BigDecimal hitRate,
      boolean verificationActive) {}

  /** Shared metrics for future private groups. Never serialize this record as a public roster. */
  public record MemberMetrics(
      int position, long wins, long losses, BigDecimal hitRate, boolean verificationActive) {}
}
