package br.com.betufu.sports.internal.odds;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.VersionStatus;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.util.List;
import java.util.Objects;

final class SportsOutcomeForOddsMapper {

  SportsEventForOdds mapEvent(SportsOddsSourceRepository.EventRow event) {
    Objects.requireNonNull(event, "event must not be null");
    return new SportsEventForOdds(
        event.eventId(),
        event.competitionId(),
        event.editionId(),
        event.sportId(),
        event.categoryId(),
        event.scheduledAt(),
        event.zoneId(),
        SportsEventForOdds.EventFormat.valueOf(event.format()),
        event.slots().stream()
            .map(
                slot ->
                    new SportsEventForOdds.Slot(
                        slot.slotNo(), slot.displayLabel(), slot.teamId(), slot.teamName()))
            .toList());
  }

  ConfirmedSportsResultForOdds mapOutcome(SportsOddsSourceRepository.OutcomeRow outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    SportsResultKindForOdds kind = SportsResultKindForOdds.valueOf(outcome.outcomeKind());
    List<Score> scores =
        outcome.scores().stream()
            .map(
                score ->
                    new Score(score.slotNo(), score.score(), score.teamId(), score.displayLabel()))
            .toList();
    VersionStatus status =
        outcome.confirmedVersionStatus() == null
            ? VersionStatus.CONFIRMADO
            : VersionStatus.valueOf(outcome.confirmedVersionStatus());
    return new ConfirmedSportsResultForOdds(
        outcome.confirmedVersionId() == null ? outcome.factId() : outcome.confirmedVersionId(),
        outcome.eventId(),
        outcome.competitionId(),
        outcome.editionId(),
        outcome.versionNo(),
        status,
        kind,
        scores,
        outcome.confirmedAt() == null ? outcome.knownAt() : outcome.confirmedAt(),
        outcome.knownAt(),
        outcome.sportTime(),
        outcome.supersedesVersionId());
  }
}
