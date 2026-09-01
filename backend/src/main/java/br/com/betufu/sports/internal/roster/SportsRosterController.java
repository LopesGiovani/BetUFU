package br.com.betufu.sports.internal.roster;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterCorrectRequest;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterDraft;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterPublication;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class SportsRosterController {

  private final SportsRosterService service;

  public SportsRosterController(SportsRosterService service) {
    this.service = service;
  }

  @GetMapping(path = "/teams/{teamId}/roster", produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsRosterDraft getRosterDraft(
      @PathVariable UUID teamId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.getRosterDraft(actor.issuer(), actor.subject(), teamId);
  }

  @PutMapping(
      path = "/teams/{teamId}/roster",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsRosterDraft updateRosterDraft(
      @PathVariable UUID teamId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody SportsRosterUpdateRequest request) {
    Actor actor = actor(user);
    return service.updateRosterDraft(
        actor.issuer(), actor.subject(), teamId, expectedLockVersion, request);
  }

  @PostMapping(path = "/teams/{teamId}/roster/publish", produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsRosterPublication publishRoster(
      @PathVariable UUID teamId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.publishRoster(actor.issuer(), actor.subject(), teamId, expectedLockVersion);
  }

  @PostMapping(
      path = "/teams/{teamId}/roster/correct",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsRosterPublication correctRoster(
      @PathVariable UUID teamId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody SportsRosterCorrectRequest request) {
    Actor actor = actor(user);
    return service.correctRoster(
        actor.issuer(), actor.subject(), teamId, expectedLockVersion, request);
  }

  @GetMapping(
      path = "/teams/{teamId}/roster/publication",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SportsRosterPublication getLatestPublication(
      @PathVariable UUID teamId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.getLatestPublication(actor.issuer(), actor.subject(), teamId);
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
