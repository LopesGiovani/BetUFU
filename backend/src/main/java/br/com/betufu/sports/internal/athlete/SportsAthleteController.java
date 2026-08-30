package br.com.betufu.sports.internal.athlete;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import br.com.betufu.sports.internal.athlete.SportsAthleteModels.SportsAthleteCreateRequest;
import br.com.betufu.sports.internal.athlete.SportsAthleteModels.SportsAthleteSummary;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(path = "/api/v1/operations/sports")
public class SportsAthleteController {

    private final SportsAthleteService service;

    public SportsAthleteController(SportsAthleteService service) {
        this.service = service;
    }

    @GetMapping(path = "/athletes", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SportsAthleteSummary> listAthletes(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
        Actor actor = actor(user);
        return service.listAthletes(actor.issuer(), actor.subject());
    }

    @PostMapping(path = "/teams/{teamId}/athletes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SportsAthleteSummary> createAthlete(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
            @Valid @RequestBody SportsAthleteCreateRequest request) {
        Actor actor = actor(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createAthlete(actor.issuer(), actor.subject(), teamId, request));
    }

    @PutMapping(path = "/athletes/{athleteId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SportsAthleteSummary updateAthlete(
            @PathVariable UUID athleteId,
            @RequestParam Long expectedLockVersion,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
            @Valid @RequestBody NameSlugRequest request) {
        Actor actor = actor(user);
        return service.updateAthlete(actor.issuer(), actor.subject(), athleteId, request, expectedLockVersion);
    }

    @PostMapping(path = "/athletes/{athleteId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    public SportsAthleteSummary publishAthlete(
            @PathVariable UUID athleteId,
            @RequestParam Long expectedLockVersion,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
        Actor actor = actor(user);
        return service.publishAthlete(actor.issuer(), actor.subject(), athleteId, expectedLockVersion);
    }

    private static Actor actor(OidcUser user) {
        if (user == null) {
            throw unauthorized();
        }
        Object issuerValue = user.getClaims().get(IdTokenClaimNames.ISS);
        String issuer = switch (issuerValue) {
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

    private record Actor(String issuer, String subject) { }
}
