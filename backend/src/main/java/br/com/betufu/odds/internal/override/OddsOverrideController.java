package br.com.betufu.odds.internal.override;

import br.com.betufu.odds.internal.override.OddsOverrideModels.ApproveOverrideRequest;
import br.com.betufu.odds.internal.override.OddsOverrideModels.OverrideView;
import br.com.betufu.odds.internal.override.OddsOverrideModels.ProposeOverrideRequest;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(
    path = "/api/v1/operations/odds/markets/{marketId}/overrides",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class OddsOverrideController {
  private final OddsOverrideService service;

  public OddsOverrideController(OddsOverrideService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public OverrideView propose(
      @PathVariable UUID marketId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @RequestBody ProposeOverrideRequest request) {
    Actor actor = actor(user);
    return service.propose(actor.issuer(), actor.subject(), marketId, idempotencyKey, request);
  }

  @PostMapping(path = "/{proposalId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
  public OverrideView approve(
      @PathVariable UUID marketId,
      @PathVariable UUID proposalId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @RequestBody ApproveOverrideRequest request) {
    Actor actor = actor(user);
    return service.approve(
        actor.issuer(), actor.subject(), marketId, proposalId, idempotencyKey, request);
  }

  private static Actor actor(OidcUser user) {
    if (user == null) throw unauthorized();
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
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
      throw unauthorized();
    return new Actor(issuer, subject);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private record Actor(String issuer, String subject) {}
}
