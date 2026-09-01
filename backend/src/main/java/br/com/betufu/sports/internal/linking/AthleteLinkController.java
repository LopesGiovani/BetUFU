package br.com.betufu.sports.internal.linking;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkDecisionRequest;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposal;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkReviewPage;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class AthleteLinkController {

  private final AthleteLinkService service;

  public AthleteLinkController(AthleteLinkService service) {
    this.service = service;
  }

  @GetMapping(
      path = "/api/v1/me/sports/athlete-links/proposal",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteLinkProposal getMyPendingProposal(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.getMyPendingProposal(actor.issuer(), actor.subject());
  }

  @PutMapping(
      path = "/api/v1/me/sports/athlete-links/{proposalId}/decision",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteLinkProposal decideProposal(
      @PathVariable UUID proposalId,
      @Valid @RequestBody AthleteLinkDecisionRequest request,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.decideProposal(actor.issuer(), actor.subject(), proposalId, request);
  }

  @GetMapping(
      path = "/api/v1/operations/sports/athlete-link-reviews",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteLinkReviewPage listReviews(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.listReviews(actor.issuer(), actor.subject(), page, size);
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
