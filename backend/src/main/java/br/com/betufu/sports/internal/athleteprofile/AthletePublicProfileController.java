package br.com.betufu.sports.internal.athleteprofile;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthletePublicProfile;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthletePublicProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class AthletePublicProfileController {

    private final AthletePublicProfileService service;

    public AthletePublicProfileController(AthletePublicProfileService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/v1/me/sports/athlete-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public AthletePublicProfile getMyProfile(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
        Actor actor = actor(user);
        return service.getMyProfile(actor.issuer(), actor.subject());
    }

    @PutMapping(
            path = "/api/v1/me/sports/athlete-profile",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AthletePublicProfile updateMyProfile(
            @Valid @RequestBody AthletePublicProfileUpdateRequest request,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
        Actor actor = actor(user);
        return service.updateMyProfile(actor.issuer(), actor.subject(), request);
    }

    @DeleteMapping(path = "/api/v1/me/sports/athlete-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public AthletePublicProfile clearMyProfile(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
        Actor actor = actor(user);
        return service.clearMyProfile(actor.issuer(), actor.subject());
    }

    @GetMapping(path = "/api/v1/public/sports/athletes/{athleteId}/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public AthletePublicProfile getPublicProfile(@PathVariable UUID athleteId) {
        return service.getPublicProfile(athleteId);
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

    private record Actor(String issuer, String subject) {}
}
