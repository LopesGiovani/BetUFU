package br.com.betufu.sports.internal.event;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventCancelRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventCreateRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventDetail;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventPostponeRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSuspendRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventUpdateRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultConfirmRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultCorrectRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultInformRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultReviewRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultVersion;
import br.com.betufu.sports.internal.result.SportsResultService;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class SportsEventResultController {

  static final String EDITION_EVENTS_PATH = "/api/v1/operations/sports/editions/{editionId}/events";
  static final String EVENT_PATH = "/api/v1/operations/sports/events/{eventId}";
  static final String EVENT_PUBLISH_PATH = "/api/v1/operations/sports/events/{eventId}/publish";
  static final String EVENT_SUSPEND_PATH = "/api/v1/operations/sports/events/{eventId}/suspend";
  static final String EVENT_POSTPONE_PATH = "/api/v1/operations/sports/events/{eventId}/postpone";
  static final String EVENT_CANCEL_PATH = "/api/v1/operations/sports/events/{eventId}/cancel";

  static final String EVENT_RESULTS_PATH = "/api/v1/operations/sports/events/{eventId}/results";
  static final String EVENT_RESULT_INFORM_PATH =
      "/api/v1/operations/sports/events/{eventId}/results/inform";
  static final String EVENT_RESULT_REVIEW_PATH =
      "/api/v1/operations/sports/events/{eventId}/results/review";
  static final String EVENT_RESULT_CONFIRM_PATH =
      "/api/v1/operations/sports/events/{eventId}/results/confirm";
  static final String EVENT_RESULT_CORRECT_PATH =
      "/api/v1/operations/sports/events/{eventId}/results/correct";

  private final SportsEventService eventService;
  private final SportsResultService resultService;

  public SportsEventResultController(
      SportsEventService eventService, SportsResultService resultService) {
    this.eventService = Objects.requireNonNull(eventService, "eventService must not be null");
    this.resultService = Objects.requireNonNull(resultService, "resultService must not be null");
  }

  @GetMapping(path = EDITION_EVENTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SportsEventDetail>> listEvents(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    return ResponseEntity.ok(
        eventService.listEventsByEdition(actor.issuer(), actor.subject(), editionId));
  }

  @PostMapping(
      path = EDITION_EVENTS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> createEvent(
      @PathVariable UUID editionId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsEventCreateRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail created =
        eventService.createEvent(actor.issuer(), actor.subject(), editionId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping(path = EVENT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> getEventDetail(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    return ResponseEntity.ok(eventService.getEventDetail(actor.issuer(), actor.subject(), eventId));
  }

  @PutMapping(
      path = EVENT_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> updateEventDraft(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsEventUpdateRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail updated =
        eventService.updateEventDraft(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(updated);
  }

  @PostMapping(path = EVENT_PUBLISH_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> publishEvent(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail published =
        eventService.publishEvent(actor.issuer(), actor.subject(), eventId, expectedLockVersion);
    return ResponseEntity.ok(published);
  }

  @PostMapping(
      path = EVENT_SUSPEND_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> suspendEvent(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @RequestBody(required = false) SportsEventSuspendRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail suspended =
        eventService.suspendEvent(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(suspended);
  }

  @PostMapping(
      path = EVENT_POSTPONE_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> postponeEvent(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsEventPostponeRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail postponed =
        eventService.postponeEvent(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(postponed);
  }

  @PostMapping(
      path = EVENT_CANCEL_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsEventDetail> cancelEvent(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsEventCancelRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsEventDetail cancelled =
        eventService.cancelEvent(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(cancelled);
  }

  @GetMapping(path = EVENT_RESULTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SportsResultVersion>> listResultVersions(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    ActorClaims actor = requireActor(oidcUser);
    return ResponseEntity.ok(
        resultService.getResultVersions(actor.issuer(), actor.subject(), eventId));
  }

  @PostMapping(
      path = EVENT_RESULT_INFORM_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsResultVersion> informResult(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsResultInformRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsResultVersion version =
        resultService.informResult(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(version);
  }

  @PostMapping(
      path = EVENT_RESULT_REVIEW_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsResultVersion> reviewResult(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsResultReviewRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsResultVersion version =
        resultService.submitForReviewResult(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(version);
  }

  @PostMapping(
      path = EVENT_RESULT_CONFIRM_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsResultVersion> confirmResult(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsResultConfirmRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsResultVersion version =
        resultService.confirmResult(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(version);
  }

  @PostMapping(
      path = EVENT_RESULT_CORRECT_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SportsResultVersion> correctResult(
      @PathVariable UUID eventId,
      @RequestParam Long expectedLockVersion,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody SportsResultCorrectRequest request) {
    ActorClaims actor = requireActor(oidcUser);
    SportsResultVersion version =
        resultService.correctResult(
            actor.issuer(), actor.subject(), eventId, expectedLockVersion, request);
    return ResponseEntity.ok(version);
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
    String text =
        switch (value) {
          case String stringValue -> stringValue;
          case URL urlValue -> urlValue.toString();
          case URI uriValue -> uriValue.toString();
          case null, default -> null;
        };
    return text != null && !text.isBlank() ? text : null;
  }

  private record ActorClaims(String issuer, String subject) {}
}
