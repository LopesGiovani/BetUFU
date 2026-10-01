package br.com.betufu.settlement;

/** Business outcome of an immutable settlement unit; processing progress lives separately. */
public enum SettlementState {
  PENDING,
  WON,
  LOST,
  REFUNDED,
  ANNULLED
}
