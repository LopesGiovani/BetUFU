package br.com.betufu.sports.internal.roster;

import br.com.betufu.sports.SportsRosterForOddsPort;
import br.com.betufu.sports.SportsRosterForOddsPort.SportsRosterBindingForOdds;
import br.com.betufu.sports.SportsRosterSnapshotForOdds;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Adapts the internal roster read model to the minimal public odds port. */
@Component
@Profile("runtime | integrated")
public class SportsRosterForOddsAdapter implements SportsRosterForOddsPort {

  private final JdbcTemplate jdbc;
  private final SportsEventRosterBindingRepository bindingRepository;
  private final TransactionTemplate transactions;

  public SportsRosterForOddsAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    this.bindingRepository = new SportsEventRosterBindingRepository(jdbc);
    this.transactions = null;
  }

  @Autowired
  public SportsRosterForOddsAdapter(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    this.bindingRepository = new SportsEventRosterBindingRepository(jdbc);
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Override
  public Optional<SportsRosterSnapshotForOdds> findPublishedSnapshot(
      UUID teamId, UUID publicationId, long versionNo) {
    if (teamId == null || publicationId == null || versionNo <= 0) return Optional.empty();
    return jdbc
        .query(
            """
                SELECT p.team_id, p.competition_id, p.edition_id, p.publication_id,
                       p.version_no, p.published_at
                FROM public.sports_roster_publication p
                JOIN public.sports_roster r ON r.roster_id = p.roster_id
                WHERE p.team_id = ? AND p.publication_id = ? AND p.version_no = ?
                  AND r.status = 'PUBLICADO'
                """,
            (rs, rowNum) ->
                new SportsRosterSnapshotForOdds(
                    rs.getObject("team_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("publication_id", UUID.class),
                    rs.getLong("version_no"),
                    rs.getTimestamp("published_at").toInstant()),
            teamId,
            publicationId,
            versionNo)
        .stream()
        .findFirst();
  }

  @Override
  public List<SportsRosterBindingForOdds> bindMarketVersion(
      UUID marketVersionId, UUID eventId, List<SportsRosterSnapshotForOdds> snapshots) {
    if (transactions == null) {
      throw new IllegalStateException("transaction manager is required for binding");
    }
    return transactions.execute(
        status -> {
          List<SportsEventRosterBindingRepository.EventSlot> slots =
              bindingRepository.lockHeadToHeadEvent(eventId);
          if (slots.size() != 2 || snapshots == null || snapshots.size() != 2) {
            throw new IllegalStateException("event is not an eligible published H2H event");
          }
          for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            var snapshot = snapshots.get(i);
            if (!slot.teamId().equals(snapshot.teamId())
                || !slot.competitionId().equals(snapshot.competitionId())
                || !slot.editionId().equals(snapshot.editionId())
                || findPublishedSnapshot(
                        snapshot.teamId(), snapshot.publicationId(), snapshot.versionNo())
                    .isEmpty()) {
              throw new IllegalStateException(
                  "roster binding failed closed: stale or mismatched snapshot");
            }
            bindingRepository.insertBinding(
                marketVersionId,
                slot,
                snapshot.publicationId(),
                snapshot.versionNo(),
                snapshot.knownAt());
          }
          return bindingRepository.findBindings(marketVersionId).stream()
              .map(
                  binding ->
                      new SportsRosterBindingForOdds(
                          binding.marketVersionId(),
                          binding.eventId(),
                          binding.slotNo(),
                          binding.teamId(),
                          binding.competitionId(),
                          binding.editionId(),
                          binding.publicationId(),
                          binding.versionNo(),
                          binding.knownAt()))
              .toList();
        });
  }
}
