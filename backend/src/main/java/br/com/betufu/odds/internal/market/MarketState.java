package br.com.betufu.odds.internal.market;

/** Lifecycle owned by an odds market, independent from sports_event lifecycle. */
public enum MarketState {
  DRAFT,
  OPEN,
  SUSPENDED,
  CLOSED,
  LOCKED,
  CANCELLED
}
