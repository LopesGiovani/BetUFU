package br.com.betufu.sports;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Minimal, immutable event projection used to decide pre-game market eligibility. */
public record SportsEventForOdds(
    UUID eventId,
    UUID competitionId,
    UUID editionId,
    UUID sportId,
    UUID categoryId,
    Instant scheduledAt,
    String zoneId,
    EventFormat format,
    List<Slot> slots) {

  public SportsEventForOdds {
    require(eventId, "eventId");
    require(competitionId, "competitionId");
    require(editionId, "editionId");
    require(sportId, "sportId");
    require(categoryId, "categoryId");
    require(scheduledAt, "scheduledAt");
    if (zoneId == null || zoneId.isBlank()) {
      throw new IllegalArgumentException("zoneId must not be blank");
    }
    require(format, "format");
    if (slots == null || slots.size() != 2) {
      throw new IllegalArgumentException("HEAD_TO_HEAD events require exactly two slots");
    }
    slots = List.copyOf(slots);
    if (slots.get(0).slotNo() != 1 || slots.get(1).slotNo() != 2) {
      throw new IllegalArgumentException("slots must be numbered 1 and 2");
    }
    if (slots.get(0).teamId().equals(slots.get(1).teamId())) {
      throw new IllegalArgumentException("slots must reference distinct teams");
    }
  }

  private static void require(Object value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
  }

  public enum EventFormat {
    HEAD_TO_HEAD
  }

  public record Slot(int slotNo, String displayLabel, UUID teamId, String teamName) {
    public Slot {
      if (slotNo < 1) throw new IllegalArgumentException("slotNo must be positive");
      if (displayLabel == null || displayLabel.isBlank()) {
        throw new IllegalArgumentException("displayLabel must not be blank");
      }
      require(teamId, "teamId");
      if (teamName == null || teamName.isBlank()) {
        throw new IllegalArgumentException("teamName must not be blank");
      }
    }
  }
}
