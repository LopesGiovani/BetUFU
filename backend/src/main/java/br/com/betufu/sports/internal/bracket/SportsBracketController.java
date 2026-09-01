package br.com.betufu.sports.internal.bracket;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCorrectRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCreateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketPublishRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSummary;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketUpdateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketView;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;
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
public class SportsBracketController {

  static final String PUBLIC_BRACKET_PATH = "/api/v1/public/sports/brackets/{bracketId}";
  static final String EDITION_BRACKETS_PATH =
      "/api/v1/operations/sports/editions/{editionId}/brackets";
  static final String BRACKET_PATH = "/api/v1/operations/sports/brackets/{bracketId}";
  static final String BRACKET_PUBLISH_PATH =
      "/api/v1/operations/sports/brackets/{bracketId}/publish";
  static final String BRACKET_CORRECT_PATH =
      "/api/v1/operations/sports/brackets/{bracketId}/correct";

  private final SportsBracketService service;

  public SportsBracketController(SportsBracketService service) {
    this.service = Objects.requireNonNull(service, "service must not be null");
  }

  @GetMapping(path = PUBLIC_BRACKET_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketView> getPublicBracket(@PathVariable UUID bracketId) {
    return ResponseEntity.ok(service.getPublicBracket(bracketId));
  }

  @GetMapping(path = EDITION_BRACKETS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SportsBracketSummary>> listBrackets(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    return ResponseEntity.ok(
        service.listBracketsByEdition(actor.issuer(), actor.subject(), editionId));
  }

  @PostMapping(
      path = EDITION_BRACKETS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketSummary> createBracket(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsBracketCreateRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsBracketSummary created =
        service.createBracket(actor.issuer(), actor.subject(), editionId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping(path = BRACKET_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketView> getBracketPreview(
      @PathVariable UUID bracketId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    return ResponseEntity.ok(service.getBracketPreview(actor.issuer(), actor.subject(), bracketId));
  }

  @PutMapping(
      path = BRACKET_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketView> updateBracket(
      @PathVariable UUID bracketId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsBracketUpdateRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsBracketView updated =
        service.updateBracket(
            actor.issuer(), actor.subject(), bracketId, expectedLockVersion, request);
    return ResponseEntity.ok(updated);
  }

  @PostMapping(
      path = BRACKET_PUBLISH_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketView> publishBracket(
      @PathVariable UUID bracketId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsBracketPublishRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsBracketView published =
        service.publishBracket(
            actor.issuer(), actor.subject(), bracketId, expectedLockVersion, request);
    return ResponseEntity.ok(published);
  }

  @PostMapping(
      path = BRACKET_CORRECT_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsBracketView> correctBracket(
      @PathVariable UUID bracketId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsBracketCorrectRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsBracketView corrected =
        service.correctBracket(
            actor.issuer(), actor.subject(), bracketId, expectedLockVersion, request);
    return ResponseEntity.ok(corrected);
  }

  private static ActorClaims requireActor(OidcUser oidcUser) {
    if (oidcUser == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }

    String issuer = extractIssuer(oidcUser);
    Object subjectClaim = oidcUser.getClaims().get(IdTokenClaimNames.SUB);
    String subject = subjectClaim instanceof String value && !value.isBlank() ? value : null;
    if (issuer == null || subject == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return new ActorClaims(issuer, subject);
  }

  private static String extractIssuer(OidcUser oidcUser) {
    Object value = oidcUser.getClaims().get(IdTokenClaimNames.ISS);
    String text =
        switch (value) {
          case String stringValue -> stringValue;
          case URL urlValue -> urlValue.toString();
          case URI uriValue -> uriValue.toString();
          case null, default -> null;
        };
    return text != null && !text.isBlank() ? text : null;
  }

  private record ActorClaims(String issuer, String subject) {}
}
