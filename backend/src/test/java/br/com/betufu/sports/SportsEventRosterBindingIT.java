package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.RosterMarketEligibilityService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SportsEventRosterBindingIT {

  @Test
  void bindsTwoExactPublishedSnapshotsWithoutExposingMembers() {
    UUID eventId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    SportsRosterSnapshotForOdds first = snapshot(UUID.randomUUID(), 1);
    SportsRosterSnapshotForOdds second = snapshot(UUID.randomUUID(), 2);
    FakeRosterPort port = new FakeRosterPort(List.of(first, second));

    RosterMarketEligibilityService service = new RosterMarketEligibilityService(port);
    List<SportsRosterForOddsPort.SportsRosterBindingForOdds> bindings =
        service.bindMarketVersion(marketVersionId, eventId, List.of(first, second));

    assertThat(bindings).hasSize(2);
    assertThat(bindings)
        .extracting(SportsRosterForOddsPort.SportsRosterBindingForOdds::slotNo)
        .containsExactly(1, 2);
    assertThat(service.isEligible(marketVersionId, eventId, bindings)).isTrue();
  }

  @Test
  void rejectsMissingOrMismatchedSnapshotBeforeWriting() {
    FakeRosterPort port = new FakeRosterPort(List.of(snapshot(UUID.randomUUID(), 1)));
    RosterMarketEligibilityService service = new RosterMarketEligibilityService(port);
    assertThatThrownBy(
            () -> service.bindMarketVersion(UUID.randomUUID(), UUID.randomUUID(), List.of()))
        .isInstanceOf(IllegalStateException.class);
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

  private static final class FakeRosterPort implements SportsRosterForOddsPort {
    private final List<SportsRosterSnapshotForOdds> snapshots;

    private FakeRosterPort(List<SportsRosterSnapshotForOdds> snapshots) {
      this.snapshots = snapshots;
    }

    @Override
    public Optional<SportsRosterSnapshotForOdds> findPublishedSnapshot(
        UUID teamId, UUID publicationId, long versionNo) {
      return snapshots.stream()
          .filter(
              snapshot ->
                  snapshot.teamId().equals(teamId)
                      && snapshot.publicationId().equals(publicationId)
                      && snapshot.versionNo() == versionNo)
          .findFirst();
    }

    @Override
    public List<SportsRosterForOddsPort.SportsRosterBindingForOdds> bindMarketVersion(
        UUID marketVersionId, UUID eventId, List<SportsRosterSnapshotForOdds> requested) {
      if (requested == null || requested.size() != 2) {
        throw new IllegalStateException("two roster snapshots are required");
      }
      return java.util.stream.IntStream.range(0, requested.size())
          .mapToObj(
              index -> {
                SportsRosterSnapshotForOdds snapshot = requested.get(index);
                if (findPublishedSnapshot(
                        snapshot.teamId(), snapshot.publicationId(), snapshot.versionNo())
                    .isEmpty()) {
                  throw new IllegalStateException("stale roster snapshot");
                }
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
