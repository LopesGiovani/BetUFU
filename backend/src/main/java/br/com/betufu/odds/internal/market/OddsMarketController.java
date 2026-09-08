package br.com.betufu.odds.internal.market;

import br.com.betufu.odds.internal.market.OddsMarketModels.CreateMarketRequest;
import br.com.betufu.odds.internal.market.OddsMarketModels.LifecycleRequest;
import br.com.betufu.odds.internal.market.OddsMarketModels.MarketView;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(
    path = "/api/v1/operations/odds/markets",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class OddsMarketController {
  private final OddsMarketService service;

  public OddsMarketController(OddsMarketService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MarketView> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @RequestBody CreateMarketRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createMarket(actor.issuer(), actor.subject(), idempotencyKey, request));
  }

  @GetMapping("/{marketId}")
  public MarketView get(@PathVariable UUID marketId) {
    return service.getMarket(marketId);
  }

  @PostMapping("/{marketId}/lifecycle")
  public MarketView lifecycle(
      @PathVariable UUID marketId,
      @RequestParam long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @RequestBody LifecycleRequest request) {
    Actor actor = actor(user);
    return service.transition(
        actor.issuer(),
        actor.subject(),
        marketId,
        request.action(),
        expectedLockVersion,
        request.reason(),
        java.util.List.of(),
        false);
  }

  private static Actor actor(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
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
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    return new Actor(issuer, subject);
  }

  private record Actor(String issuer, String subject) {}
}
