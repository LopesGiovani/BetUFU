package br.com.betufu.identity.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class ProfileController {

    private static final String PROFILE_ME_PATH = "/api/v1/profile/me";
    private static final String DEFAULT_MATRICULA_STATUS = "NAO_REGISTRADA";

    private final BetufuProfileService profileService;
    private final LocalIdentityService localIdentityService;
    private final LocalIdentityRepository localIdentityRepository;

    public ProfileController(
            BetufuProfileService profileService,
            LocalIdentityService localIdentityService,
            LocalIdentityRepository localIdentityRepository) {
        this.profileService = Objects.requireNonNull(profileService, "profileService must not be null");
        this.localIdentityService = Objects.requireNonNull(localIdentityService, "localIdentityService must not be null");
        this.localIdentityRepository = Objects.requireNonNull(localIdentityRepository, "localIdentityRepository must not be null");
    }

    @GetMapping(path = PROFILE_ME_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentProfileResponse> getMyProfile(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        Optional<OidcClaimsProjection> claims = OidcClaimsProjection.from(oidcUser);
        if (claims.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return findProfileByClaims(claims.get())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping(
            path = PROFILE_ME_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentProfileResponse> putMyProfile(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody ProfileUpdateRequest request) {
        Optional<OidcClaimsProjection> claims = OidcClaimsProjection.from(oidcUser);
        if (claims.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CurrentProfileResponse response = saveProfileByClaims(claims.get(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping(
            path = PROFILE_ME_PATH + "/favorite-organization",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentProfileResponse> putMyFavoriteOrganization(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody CurrentProfileResponse.FavoriteOrganizationRequest request) {
        Optional<OidcClaimsProjection> claims = OidcClaimsProjection.from(oidcUser);
        if (claims.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LocalIdentity identity = localIdentityService.persistCurrentIdentity(claims.get().issuer(), claims.get().subject());
        BetufuProfile profile = profileService.saveFavoriteOrganization(identity.getIdentityId(), request.organizationId());
        return ResponseEntity.ok(toResponse(identity.getIdentityId(), profile));
    }

    private Optional<CurrentProfileResponse> findProfileByClaims(OidcClaimsProjection claims) {
        return localIdentityRepository.findByNaturalKey(claims.issuer(), claims.subject())
                .flatMap(identity -> profileService.findProfile(identity.getIdentityId())
                        .map(profile -> toResponse(identity.getIdentityId(), profile)));
    }

    private CurrentProfileResponse saveProfileByClaims(
            OidcClaimsProjection claims, ProfileUpdateRequest request) {
        LocalIdentity identity = localIdentityService.persistCurrentIdentity(claims.issuer(), claims.subject());
        BetufuProfile profile = profileService.saveProfile(identity.getIdentityId(), request.nickname());
        return toResponse(identity.getIdentityId(), profile);
    }

    private static CurrentProfileResponse toResponse(UUID identityId, BetufuProfile profile) {
        return new CurrentProfileResponse(
                identityId,
                profile.getNickname(),
                profile.getNicknameUpdatedAt(),
                DEFAULT_MATRICULA_STATUS,
                profile.getFavoriteOrganizationId());
    }
}
