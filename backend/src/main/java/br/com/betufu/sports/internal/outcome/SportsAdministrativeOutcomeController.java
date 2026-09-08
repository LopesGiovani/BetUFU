package br.com.betufu.sports.internal.outcome;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.AdministrativeOutcomeRequest;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeFact;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@Profile("runtime | integrated")
public class SportsAdministrativeOutcomeController {
  private final SportsOutcomeService service;
  public SportsAdministrativeOutcomeController(SportsOutcomeService service) { this.service = service; }

  @PostMapping(path = "/api/v1/operations/sports/events/{eventId}/outcomes/administrative", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OutcomeFact> record(@PathVariable UUID eventId, @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user, @Valid @RequestBody AdministrativeOutcomeRequest request) {
    if (user == null) throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    String issuer = claim(user, IdTokenClaimNames.ISS); String subject = claim(user, IdTokenClaimNames.SUB);
    return ResponseEntity.ok(service.recordAdministrativeOutcome(issuer, subject, eventId, expectedLockVersion, request));
  }

  private static String claim(OidcUser user, String name) {
    Object value = user.getClaims().get(name);
    return switch (value) { case String s -> s.isBlank() ? null : s; case URL u -> u.toString(); case URI u -> u.toString(); case null, default -> null; };
  }
}
