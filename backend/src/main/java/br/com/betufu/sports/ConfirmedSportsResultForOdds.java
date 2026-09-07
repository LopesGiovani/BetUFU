package br.com.betufu.sports;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Versioned result projection; administrative facts never carry a sports score. */
public record ConfirmedSportsResultForOdds(
    UUID versionId,
    UUID eventId,
    UUID competitionId,
    UUID editionId,
    long versionNo,
    VersionStatus status,
    SportsResultKindForOdds resultKind,
    List<Score> scores,
    Instant confirmedAt,
    Instant knownAt,
    Instant sportTime,
    UUID supersedesVersionId) {

  public ConfirmedSportsResultForOdds {
    require(versionId, "versionId");
    require(eventId, "eventId");
    require(competitionId, "competitionId");
    require(editionId, "editionId");
    if (versionNo < 1) throw new IllegalArgumentException("versionNo must be positive");
    require(status, "status");
    require(resultKind, "resultKind");
    require(confirmedAt, "confirmedAt");
    require(knownAt, "knownAt");
    require(sportTime, "sportTime");
    if (confirmedAt.isAfter(knownAt) || sportTime.isAfter(knownAt)) {
      throw new IllegalArgumentException("confirmedAt and sportTime must not be after knownAt");
    }
    scores = scores == null ? List.of() : List.copyOf(scores);
    if (resultKind.hasSportsScore()) {
      if (scores.size() != 2 || scores.get(0).slotNo() != 1 || scores.get(1).slotNo() != 2) {
        throw new IllegalArgumentException("SPORTING_RESULT requires two ordered scores");
      }
    } else if (!scores.isEmpty()) {
      throw new IllegalArgumentException("administrative and lifecycle facts cannot carry scores");
    }
  }

  private static void require(Object value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
  }

  public enum VersionStatus {
    CONFIRMADO,
    CORRIGIDO
  }

  public record Score(int slotNo, int score, UUID teamId, String displayLabel) {
    public Score {
      if (slotNo < 1) throw new IllegalArgumentException("slotNo must be positive");
      if (score < 0) throw new IllegalArgumentException("score must not be negative");
      require(teamId, "teamId");
      if (displayLabel == null || displayLabel.isBlank()) {
        throw new IllegalArgumentException("displayLabel must not be blank");
      }
    }
  }
}
