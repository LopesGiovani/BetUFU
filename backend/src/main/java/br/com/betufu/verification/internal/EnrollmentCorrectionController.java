package br.com.betufu.verification.internal;

import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the governed enrollment correction operation (D-32). */
@RestController
@Profile("runtime | integrated")
public class EnrollmentCorrectionController {

    static final String ENDPOINT_PATH = "/api/v1/operations/enrollments/correction";

    private final EnrollmentCorrectionService correctionService;
    private final OperationalIdentityLookup identityLookup;

    public EnrollmentCorrectionController(
            EnrollmentCorrectionService correctionService,
            OperationalIdentityLookup identityLookup) {
        this.correctionService = Objects.requireNonNull(
                correctionService, "correctionService must not be null");
        this.identityLookup = Objects.requireNonNull(
                identityLookup, "identityLookup must not be null");
    }

    @PostMapping(
            path = ENDPOINT_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrollmentCorrectionResponse> correctEnrollment(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody EnrollmentCorrectionRequest request) {
        UUID actorIdentityId = resolveActorIdentityId(oidcUser);
        return ResponseEntity.ok(correctionService.correct(actorIdentityId, request));
    }

    private UUID resolveActorIdentityId(OidcUser oidcUser) {
        if (oidcUser == null || oidcUser.getIdToken() == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        Object issuerClaim = oidcUser.getIdToken().getClaims().get(IdTokenClaimNames.ISS);
        String issuer = switch (issuerClaim) {
            case String text -> text;
            case URL url -> url.toString();
            case URI uri -> uri.toString();
            case null, default -> null;
        };
        String subject = oidcUser.getIdToken().getSubject();
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        return identityLookup.findIdentityId(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }
}
