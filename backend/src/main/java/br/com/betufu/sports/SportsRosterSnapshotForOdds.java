package br.com.betufu.sports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal immutable roster publication reference consumed by odds. */
public record SportsRosterSnapshotForOdds(
    UUID teamId,
    UUID competitionId,
    UUID editionId,
    UUID publicationId,
    long versionNo,
    Instant knownAt) {

  public SportsRosterSnapshotForOdds {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(competitionId, "competitionId must not be null");
    Objects.requireNonNull(editionId, "editionId must not be null");
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    Objects.requireNonNull(knownAt, "knownAt must not be null");
    if (versionNo <= 0) throw new IllegalArgumentException("versionNo must be positive");
  }
}
