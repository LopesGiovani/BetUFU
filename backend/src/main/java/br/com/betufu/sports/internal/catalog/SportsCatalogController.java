package br.com.betufu.sports.internal.catalog;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.EditionRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.VenueRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.VenueSpaceRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class SportsCatalogController {

  public static final String ROOT = "/api/v1/operations/sports/catalog";
  private final SportsCatalogService service;

  SportsCatalogController(SportsCatalogService service) {
    this.service = service;
  }

  @GetMapping(path = ROOT + "/scope", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsCatalogScope> scope(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return ResponseEntity.ok(service.getScope(actor.issuer(), actor.subject()));
  }

  @GetMapping(
      path = ROOT + "/competitions/{competitionId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsCatalogTree> tree(
      @PathVariable UUID competitionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return ResponseEntity.ok(service.getTree(actor.issuer(), actor.subject(), competitionId));
  }

  @PostMapping(
      path = ROOT + "/competitions",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsCompetitionSummary> createCompetition(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createCompetition(actor.issuer(), actor.subject(), request));
  }

  @PostMapping(
      path = ROOT + "/competitions/{competitionId}/sports",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsSportSummary> createSport(
      @PathVariable UUID competitionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createSport(actor.issuer(), actor.subject(), competitionId, request));
  }

  @PostMapping(
      path = ROOT + "/competitions/{competitionId}/editions",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsEditionSummary> createEdition(
      @PathVariable UUID competitionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody EditionRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createEdition(actor.issuer(), actor.subject(), competitionId, request));
  }

  @PostMapping(
      path = ROOT + "/editions/{editionId}/categories",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsCategorySummary> createCategory(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createCategory(actor.issuer(), actor.subject(), editionId, request));
  }

  @PostMapping(
      path = ROOT + "/categories/{categoryId}/phases",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsPhaseSummary> createPhase(
      @PathVariable UUID categoryId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createPhase(actor.issuer(), actor.subject(), categoryId, request));
  }

  @PostMapping(
      path = ROOT + "/competitions/{competitionId}/venues",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsVenueSummary> createVenue(
      @PathVariable UUID competitionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody VenueRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createVenue(actor.issuer(), actor.subject(), competitionId, request));
  }

  @PostMapping(
      path = ROOT + "/venues/{venueId}/spaces",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsVenueSpaceSummary> createVenueSpace(
      @PathVariable UUID venueId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody VenueSpaceRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createVenueSpace(actor.issuer(), actor.subject(), venueId, request));
  }

  @PutMapping(
      path = ROOT + "/sports/{sportId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsSportSummary> updateSport(
      @PathVariable UUID sportId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updateSport(
            actor.issuer(), actor.subject(), sportId, request, expectedLockVersion));
  }

  @PutMapping(
      path = ROOT + "/editions/{editionId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsEditionSummary> updateEdition(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody EditionRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updateEdition(actor.issuer(), actor.subject(), editionId, request));
  }

  @PutMapping(
      path = ROOT + "/categories/{categoryId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsCategorySummary> updateCategory(
      @PathVariable UUID categoryId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updateCategory(
            actor.issuer(), actor.subject(), categoryId, request, expectedLockVersion));
  }

  @PutMapping(
      path = ROOT + "/phases/{phaseId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsPhaseSummary> updatePhase(
      @PathVariable UUID phaseId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody NameSlugRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updatePhase(
            actor.issuer(), actor.subject(), phaseId, request, expectedLockVersion));
  }

  @PutMapping(
      path = ROOT + "/venues/{venueId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsVenueSummary> updateVenue(
      @PathVariable UUID venueId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody VenueRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updateVenue(actor.issuer(), actor.subject(), venueId, request));
  }

  @PutMapping(
      path = ROOT + "/venue-spaces/{spaceId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SportsCatalogModels.SportsVenueSpaceSummary> updateVenueSpace(
      @PathVariable UUID spaceId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody VenueSpaceRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        service.updateVenueSpace(actor.issuer(), actor.subject(), spaceId, request));
  }

  private static Actor actor(OidcUser user) {
    if (user == null) throw unauthorized();
    String issuer =
        switch (user.getClaims().get(IdTokenClaimNames.ISS)) {
          case String value -> value;
          case URL value -> value.toString();
          case URI value -> value.toString();
          case null, default -> null;
        };
    Object subjectValue = user.getClaims().get(IdTokenClaimNames.SUB);
    String subject = subjectValue instanceof String value ? value : null;
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
      throw unauthorized();
    return new Actor(issuer, subject);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private record Actor(String issuer, String subject) {}
}
