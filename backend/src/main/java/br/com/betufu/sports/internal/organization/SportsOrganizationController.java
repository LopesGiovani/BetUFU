package br.com.betufu.sports.internal.organization;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsOrganizationSummary;
import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsTeamSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(path = "/api/v1/operations/sports")
public class SportsOrganizationController {

  private final SportsOrganizationService service;

  public SportsOrganizationController(SportsOrganizationService service) {
    this.service = service;
  }

  @GetMapping(path = "/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SportsOrganizationSummary> listOrganizations(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.listOrganizations(actor.issuer(), actor.subject());
  }

  @GetMapping(
      path = "/competitions/{competitionId}/teams",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SportsTeamSummary> listTeams(
      @PathVariable UUID competitionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.listTeams(actor.issuer(), actor.subject(), competitionId);
  }

  @PostMapping(
      path = "/organizations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsOrganizationSummary> createOrganization(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createOrganization(actor.issuer(), actor.subject(), request));
  }

  @PutMapping(
      path = "/organizations/{organizationId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsOrganizationSummary updateOrganization(
      @PathVariable UUID organizationId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return service.updateOrganization(
        actor.issuer(), actor.subject(), organizationId, request, expectedLockVersion);
  }

  @PostMapping(
      path = "/organizations/{organizationId}/publish",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsOrganizationSummary publishOrganization(
      @PathVariable UUID organizationId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.publishOrganization(
        actor.issuer(), actor.subject(), organizationId, expectedLockVersion);
  }

  @PostMapping(
      path =
          "/editions/{editionId}/sports/{sportId}/categories/{categoryId}"
              + "/organizations/{organizationId}/teams",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsTeamSummary> createTeam(
      @PathVariable UUID editionId,
      @PathVariable UUID sportId,
      @PathVariable UUID categoryId,
      @PathVariable UUID organizationId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            service.createTeam(
                actor.issuer(),
                actor.subject(),
                editionId,
                sportId,
                categoryId,
                organizationId,
                request));
  }

  @PutMapping(
      path = "/teams/{teamId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsTeamSummary updateTeam(
      @PathVariable UUID teamId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return service.updateTeam(
        actor.issuer(), actor.subject(), teamId, request, expectedLockVersion);
  }

  @PostMapping(path = "/teams/{teamId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsTeamSummary publishTeam(
      @PathVariable UUID teamId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.publishTeam(actor.issuer(), actor.subject(), teamId, expectedLockVersion);
  }

  private static Actor actor(OidcUser user) {
    if (user == null) {
      throw unauthorized();
    }
    Object issuerValue = user.getClaims().get(IdTokenClaimNames.ISS);
    String issuer =
        switch (issuerValue) {
          case String value -> value;
          case URL value -> value.toString();
          case URI value -> value.toString();
          case null, default -> null;
        };
    Object subjectValue = user.getClaims().get(IdTokenClaimNames.SUB);
    String subject = subjectValue instanceof String value ? value : null;
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw unauthorized();
    }
    return new Actor(issuer, subject);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private record Actor(String issuer, String subject) {}
}
