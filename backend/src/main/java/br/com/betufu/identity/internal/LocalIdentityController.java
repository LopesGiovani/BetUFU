package br.com.betufu.identity.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
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

    @PutMapping(
            path = "/api/v1/tracer/identity/current",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentIdentityResponse> putCurrentIdentity(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        if (oidcUser == null) {
            return unauthorized();
        }

        String issuer = oidcUser.getIdToken().getClaimAsString(IdTokenClaimNames.ISS);
        String subject = oidcUser.getIdToken().getClaimAsString(IdTokenClaimNames.SUB);

        if (isMissingOrBlank(issuer) || isMissingOrBlank(subject)) {
            return unauthorized();
        }

        LocalIdentity identity = service.persistCurrentIdentity(issuer, subject);

        return ResponseEntity.ok(new CurrentIdentityResponse(
                identity.getIdentityId(),
                PERSISTED_STATUS,
                identity.getCreatedAt(),
                identity.getUpdatedAt()));
    }

    private static boolean isMissingOrBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseEntity<CurrentIdentityResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
