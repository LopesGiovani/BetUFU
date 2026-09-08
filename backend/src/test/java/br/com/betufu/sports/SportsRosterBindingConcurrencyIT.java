package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.market.RosterMarketEligibilityService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SportsRosterBindingConcurrencyIT {

  @Test
  void concurrentBindingRequestsKeepEachSnapshotPairTogether() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    SportsRosterSnapshotForOdds v1 = snapshot(UUID.randomUUID(), 1);
    SportsRosterSnapshotForOdds v2 = snapshot(UUID.randomUUID(), 1);
    RosterMarketEligibilityService service =
        new RosterMarketEligibilityService(new DeterministicPort());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<List<SportsRosterForOddsPort.SportsRosterBindingForOdds>>> tasks =
          List.of(
              () -> service.bindMarketVersion(marketVersionId, eventId, List.of(v1, v2)),
              () -> service.bindMarketVersion(marketVersionId, eventId, List.of(v1, v2)));
      List<Future<List<SportsRosterForOddsPort.SportsRosterBindingForOdds>>> futures =
          executor.invokeAll(tasks);
      for (Future<List<SportsRosterForOddsPort.SportsRosterBindingForOdds>> future : futures) {
        List<SportsRosterForOddsPort.SportsRosterBindingForOdds> result = future.get();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).publicationId()).isEqualTo(v1.publicationId());
        assertThat(result.get(1).publicationId()).isEqualTo(v2.publicationId());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static SportsRosterSnapshotForOdds snapshot(UUID teamId, long version) {
    return new SportsRosterSnapshotForOdds(
        teamId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        version,
        Instant.parse("2026-09-08T10:00:00Z"));
  }

  private static final class DeterministicPort implements SportsRosterForOddsPort {
    @Override
    public java.util.Optional<SportsRosterSnapshotForOdds> findPublishedSnapshot(
        UUID teamId, UUID publicationId, long versionNo) {
      return java.util.Optional.of(
          new SportsRosterSnapshotForOdds(
              teamId,
              UUID.randomUUID(),
              UUID.randomUUID(),
              publicationId,
              versionNo,
              Instant.parse("2026-09-08T10:00:00Z")));
    }

    @Override
    public List<SportsRosterForOddsPort.SportsRosterBindingForOdds> bindMarketVersion(
        UUID marketVersionId, UUID eventId, List<SportsRosterSnapshotForOdds> snapshots) {
      return java.util.stream.IntStream.range(0, snapshots.size())
          .mapToObj(
              index -> {
                SportsRosterSnapshotForOdds snapshot = snapshots.get(index);
                return new SportsRosterForOddsPort.SportsRosterBindingForOdds(
                    marketVersionId,
                    eventId,
                    index + 1,
                    snapshot.teamId(),
                    snapshot.competitionId(),
                    snapshot.editionId(),
                    snapshot.publicationId(),
                    snapshot.versionNo(),
                    snapshot.knownAt());
              })
          .toList();
    }
  }
}
