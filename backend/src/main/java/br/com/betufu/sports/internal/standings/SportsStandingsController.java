package br.com.betufu.sports.internal.standings;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCorrectRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCreateRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsPublishRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsSnapshot;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsSummary;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsUpdateRequest;
import jakarta.validation.Valid;
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
public class SportsStandingsController {

    static final String PUBLIC_STANDINGS_PATH = "/api/v1/public/sports/standings/{standingsId}";
    static final String EDITION_STANDINGS_PATH = "/api/v1/operations/sports/editions/{editionId}/standings";
    static final String STANDINGS_PATH = "/api/v1/operations/sports/standings/{standingsId}";
    static final String STANDINGS_PUBLISH_PATH = "/api/v1/operations/sports/standings/{standingsId}/publish";
    static final String STANDINGS_CORRECT_PATH = "/api/v1/operations/sports/standings/{standingsId}/correct";

    private final SportsStandingsService service;

    public SportsStandingsController(SportsStandingsService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping(path = PUBLIC_STANDINGS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSnapshot> getPublicStandings(@PathVariable UUID standingsId) {
        return ResponseEntity.ok(service.getPublicStandings(standingsId));
    }

    @GetMapping(path = EDITION_STANDINGS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SportsStandingsSummary>> listStandings(
            @PathVariable UUID editionId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        ActorClaims actor = requireActor(oidcUser);
        return ResponseEntity.ok(service.listStandingsByEdition(actor.issuer(), actor.subject(), editionId));
    }

    @PostMapping(
            path = EDITION_STANDINGS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSummary> createStandings(
            @PathVariable UUID editionId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody SportsStandingsCreateRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        SportsStandingsSummary created = service.createStandings(actor.issuer(), actor.subject(), editionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping(path = STANDINGS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSnapshot> getStandingsPreview(
            @PathVariable UUID standingsId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        ActorClaims actor = requireActor(oidcUser);
        return ResponseEntity.ok(service.getStandingsPreview(actor.issuer(), actor.subject(), standingsId));
    }

    @PutMapping(
            path = STANDINGS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSnapshot> updateStandings(
            @PathVariable UUID standingsId,
            @RequestParam Long expectedLockVersion,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody SportsStandingsUpdateRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        SportsStandingsSnapshot updated = service.updateStandings(
                actor.issuer(), actor.subject(), standingsId, expectedLockVersion, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(
            path = STANDINGS_PUBLISH_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSnapshot> publishStandings(
            @PathVariable UUID standingsId,
            @RequestParam Long expectedLockVersion,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody SportsStandingsPublishRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        SportsStandingsSnapshot published = service.publishStandings(
                actor.issuer(), actor.subject(), standingsId, expectedLockVersion, request);
        return ResponseEntity.ok(published);
    }

    @PostMapping(
            path = STANDINGS_CORRECT_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsStandingsSnapshot> correctStandings(
            @PathVariable UUID standingsId,
            @RequestParam Long expectedLockVersion,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody SportsStandingsCorrectRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        SportsStandingsSnapshot corrected = service.correctStandings(
                actor.issuer(), actor.subject(), standingsId, expectedLockVersion, request);
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
        String text = switch (value) {
            case String stringValue -> stringValue;
            case URL urlValue -> urlValue.toString();
            case URI uriValue -> uriValue.toString();
            case null, default -> null;
        };
        return text != null && !text.isBlank() ? text : null;
    }

    private record ActorClaims(String issuer, String subject) {
    }
}
