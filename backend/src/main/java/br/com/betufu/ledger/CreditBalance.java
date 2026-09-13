package br.com.betufu.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** Authoritative virtual-credit balance projection for one identity. */
public record CreditBalance(UUID identityId, BigDecimal balance) {

  public CreditBalance {
    Objects.requireNonNull(identityId, "identityId must not be null");
    Objects.requireNonNull(balance, "balance must not be null");
    if (balance.scale() > 2) {
      throw new IllegalArgumentException("credit balance scale must be at most 2");
    }
    if (balance.signum() < 0) {
      throw new IllegalArgumentException("credit balance must not be negative");
    }
    balance = balance.setScale(2, RoundingMode.UNNECESSARY);
  }
}
