package br.com.betufu.sports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public sports boundary for exact, immutable roster publication references. */
public interface SportsRosterForOddsPort {

  Optional<SportsRosterSnapshotForOdds> findPublishedSnapshot(
      UUID teamId, UUID publicationId, long versionNo);

  List<SportsRosterBindingForOdds> bindMarketVersion(
      UUID marketVersionId, UUID eventId, List<SportsRosterSnapshotForOdds> snapshots);

  record SportsRosterBindingForOdds(
      UUID marketVersionId,
      UUID eventId,
      int slotNo,
      UUID teamId,
      UUID competitionId,
      UUID editionId,
      UUID publicationId,
      long versionNo,
      java.time.Instant knownAt) {}
}
