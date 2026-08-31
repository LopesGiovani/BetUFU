package br.com.betufu.sports.internal.publicview;

import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicAthleteDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicCompetitionDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEventDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicOrganizationDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamDetail;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class SportsPublicViewService {

    private final SportsPublicViewRepository repository;

    SportsPublicViewService(SportsPublicViewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PublicCompetitionDetail getCompetitionDetail(UUID competitionId) {
        if (competitionId == null) {
            throw notFound();
        }
        return repository.findCompetitionDetail(competitionId)
                .orElseThrow(SportsPublicViewService::notFound);
    }

    @Transactional(readOnly = true)
    public PublicEventDetail getEventDetail(UUID eventId) {
        if (eventId == null) {
            throw notFound();
        }
        return repository.findEventDetail(eventId)
                .orElseThrow(SportsPublicViewService::notFound);
    }

    @Transactional(readOnly = true)
    public PublicOrganizationDetail getOrganizationDetail(UUID organizationId) {
        if (organizationId == null) {
            throw notFound();
        }
        return repository.findOrganizationDetail(organizationId)
                .orElseThrow(SportsPublicViewService::notFound);
    }

    @Transactional(readOnly = true)
    public PublicTeamDetail getTeamDetail(UUID teamId) {
        if (teamId == null) {
            throw notFound();
        }
        return repository.findTeamDetail(teamId)
                .orElseThrow(SportsPublicViewService::notFound);
    }

    @Transactional(readOnly = true)
    public PublicAthleteDetail getAthleteDetail(UUID athleteId) {
        if (athleteId == null) {
            throw notFound();
        }
        return repository.findAthleteDetail(athleteId)
                .orElseThrow(SportsPublicViewService::notFound);
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }
}
