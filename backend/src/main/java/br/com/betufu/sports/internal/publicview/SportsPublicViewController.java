package br.com.betufu.sports.internal.publicview;

import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicAthleteDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicCompetitionDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicOrganizationDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamDetail;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(path = "/api/v1/public/sports", produces = MediaType.APPLICATION_JSON_VALUE)
public class SportsPublicViewController {

  private final SportsPublicViewService service;

  public SportsPublicViewController(SportsPublicViewService service) {
    this.service = service;
  }

  @GetMapping("/competitions/{competitionId}")
  public ResponseEntity<PublicCompetitionDetail> getCompetitionDetail(
      @PathVariable UUID competitionId) {
    return ResponseEntity.ok(service.getCompetitionDetail(competitionId));
  }

  @GetMapping("/organizations/{organizationId}")
  public ResponseEntity<PublicOrganizationDetail> getOrganizationDetail(
      @PathVariable UUID organizationId) {
    return ResponseEntity.ok(service.getOrganizationDetail(organizationId));
  }

  @GetMapping("/teams/{teamId}")
  public ResponseEntity<PublicTeamDetail> getTeamDetail(@PathVariable UUID teamId) {
    return ResponseEntity.ok(service.getTeamDetail(teamId));
  }

  @GetMapping("/athletes/{athleteId}")
  public ResponseEntity<PublicAthleteDetail> getAthleteDetail(@PathVariable UUID athleteId) {
    return ResponseEntity.ok(service.getAthleteDetail(athleteId));
  }
}
