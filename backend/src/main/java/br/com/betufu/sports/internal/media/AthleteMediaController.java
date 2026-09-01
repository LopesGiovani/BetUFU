package br.com.betufu.sports.internal.media;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.media.AthleteMediaModels.AthleteMediaResponse;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("runtime | integrated")
public class AthleteMediaController {

  private final AthleteMediaService service;

  public AthleteMediaController(AthleteMediaService service) {
    this.service = service;
  }

  @GetMapping(
      path = "/api/v1/me/sports/athlete-profile/photo",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteMediaResponse getMyPhoto(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.getMyMedia(actor.issuer(), actor.subject());
  }

  @PostMapping(
      path = "/api/v1/me/sports/athlete-profile/photo",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteMediaResponse uploadPhoto(
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.upload(actor.issuer(), actor.subject(), file);
  }

  @DeleteMapping(
      path = "/api/v1/me/sports/athlete-profile/photo",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AthleteMediaResponse removePhoto(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return service.remove(actor.issuer(), actor.subject());
  }

  @GetMapping(
      path = "/api/v1/public/sports/athletes/{athleteId}/profile/photo",
      produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
  public ResponseEntity<byte[]> getPublicPhoto(@PathVariable UUID athleteId) {
    AthleteMediaService.PublicMedia media = service.getPublicMedia(athleteId);
    MediaType mediaType = MediaType.parseMediaType(media.contentType());
    String etag = media.etag().startsWith("\"") ? media.etag() : "\"" + media.etag() + "\"";
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.ETAG, etag)
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic().mustRevalidate())
        .body(media.bytes());
  }

  private static Actor actor(OidcUser user) {
    if (user == null) {
      throw unauthorized();
    }
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
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw unauthorized();
    }
    return new Actor(issuer, subject);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private record Actor(String issuer, String subject) {}
}
