package br.com.betufu.sports;

/** Explicit result and lifecycle taxonomy exposed to the odds module. */
public enum SportsResultKindForOdds {
  SPORTING_RESULT,
  ADMINISTRATIVE_WALKOVER,
  ABANDONED_NO_VALID_RESULT,
  CANCELLED,
  POSTPONED;

  public boolean hasSportsScore() {
    return this == SPORTING_RESULT;
  }

  public boolean feedsRating() {
    return this == SPORTING_RESULT;
  }

  public boolean isLifecycle() {
    return this == CANCELLED || this == POSTPONED;
  }
}
