package br.com.betufu.ledger.internal;

import java.math.BigDecimal;

public final class CreditBalanceModels {
  private CreditBalanceModels() {}
  public record CreditBalanceResponse(BigDecimal balance, int scale, BigDecimal minimumStake, String rulesVersion) {}
}
