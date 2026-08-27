package br.com.betufu.sports;

import java.util.UUID;

/** Public sports-module contract used to validate competition-scoped role grants. */
public interface CompetitionScopeCatalog {

    boolean existsCompetition(UUID competitionId);
}
