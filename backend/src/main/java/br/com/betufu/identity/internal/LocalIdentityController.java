package br.com.betufu.identity.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class LocalIdentityController {

  private static final String PERSISTED_STATUS = "PERSISTED";

  private final LocalIdentityService service;

  public LocalIdentityController(LocalIdentityService service) {
    this.service = service;
  }

  @PutMapping(path = "/api/v1/tracer/identity/current", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrentIdentityResponse> putCurrentIdentity(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    return OidcClaimsProjection.from(oidcUser)
        .map(this::persist)
        .orElseGet(LocalIdentityController::unauthorized);
  }

  private ResponseEntity<CurrentIdentityResponse> persist(OidcClaimsProjection claims) {
    LocalIdentity identity = service.persistCurrentIdentity(claims.issuer(), claims.subject());

    return ResponseEntity.ok(
        new CurrentIdentityResponse(
            identity.getIdentityId(),
            PERSISTED_STATUS,
            identity.getCreatedAt(),
            identity.getUpdatedAt()));
  }

  private static ResponseEntity<CurrentIdentityResponse> unauthorized() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}
