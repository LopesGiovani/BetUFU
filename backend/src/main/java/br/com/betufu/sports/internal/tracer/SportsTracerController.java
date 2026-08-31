package br.com.betufu.sports.internal.tracer;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicEventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicSportsEventPage;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublishEventRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.VenueState;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class SportsTracerController {

    static final String COMPETITIONS_PATH = "/api/v1/operations/sports/competitions";
    static final String EDITIONS_PATH = "/api/v1/operations/sports/competitions/{competitionId}/editions";
    static final String PUBLICATIONS_PATH = "/api/v1/operations/sports/events/{eventId}/publications";
    static final String PUBLIC_EVENTS_PATH = "/api/v1/public/sports/events";
    static final String PUBLIC_EVENT_PATH = "/api/v1/public/sports/events/{eventId}";

    private final SportsTracerService service;

    public SportsTracerController(SportsTracerService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @PostMapping(
            path = COMPETITIONS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CompetitionResponse> createCompetition(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody CompetitionCreateRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        CompetitionResponse response = service.createCompetition(actor.issuer(), actor.subject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            path = EDITIONS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EditionResponse> createEdition(
            @PathVariable UUID competitionId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody EditionCreateRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        EditionResponse response = service.createEdition(
                actor.issuer(), actor.subject(), competitionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            path = PUBLICATIONS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicEventResponse> publishEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody PublishEventRequest request) {
        ActorClaims actor = requireActor(oidcUser);
        PublicEventResponse response = service.publishEvent(
                actor.issuer(), actor.subject(), eventId, request.expectedLockVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(path = PUBLIC_EVENT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicEventResponse> getPublishedEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(service.getPublishedEvent(eventId));
    }

    @GetMapping(path = PUBLIC_EVENTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicSportsEventPage> listPublishedEvents(
            @RequestParam(required = false) UUID competitionId,
            @RequestParam(required = false) UUID editionId,
            @RequestParam(required = false) UUID sportId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID phaseId,
            @RequestParam(required = false) VenueState state,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.getPublishedEvents(
                competitionId,
                editionId,
                sportId,
                categoryId,
                phaseId,
                state,
                from,
                to,
                page,
                size));
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
