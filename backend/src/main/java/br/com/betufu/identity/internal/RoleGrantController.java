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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class RoleGrantController {

    private static final String ROLES_BASE_PATH = "/api/v1/operations/roles";
    private static final String ROLES_GRANTS_PATH = "/api/v1/operations/roles/grants";
    private static final String ROLES_REVOCATIONS_PATH = "/api/v1/operations/roles/revocations";

    private final RoleGrantService roleGrantService;
    private final LocalIdentityRepository localIdentityRepository;
    private final LocalIdentityService localIdentityService;

    public RoleGrantController(
            RoleGrantService roleGrantService,
            LocalIdentityRepository localIdentityRepository,
            LocalIdentityService localIdentityService) {
        this.roleGrantService = Objects.requireNonNull(roleGrantService, "roleGrantService must not be null");
        this.localIdentityRepository = Objects.requireNonNull(localIdentityRepository, "localIdentityRepository must not be null");
        this.localIdentityService = Objects.requireNonNull(localIdentityService, "localIdentityService must not be null");
    }

    @GetMapping(path = ROLES_BASE_PATH + "/{targetIdentityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleGrantResponse> getRoles(
            @PathVariable UUID targetIdentityId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        Optional<UUID> actorIdOpt = resolveActorIdentityId(oidcUser);
        if (actorIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RoleGrantResponse response = roleGrantService.getRoleStatus(actorIdOpt.get(), targetIdentityId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            path = {ROLES_GRANTS_PATH, ROLES_BASE_PATH + "/grant"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleGrantResponse> grantRole(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody RoleGrantRequest request) {
        Optional<UUID> actorIdOpt = resolveActorIdentityId(oidcUser);
        if (actorIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RoleGrantResponse response = roleGrantService.grantRole(
                actorIdOpt.get(),
                request.targetIdentityId(),
                request.role(),
                request.scopeRef());
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            path = {ROLES_REVOCATIONS_PATH, ROLES_BASE_PATH + "/revoke"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleGrantResponse> revokeRole(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @Valid @RequestBody RoleGrantRequest request) {
        Optional<UUID> actorIdOpt = resolveActorIdentityId(oidcUser);
        if (actorIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RoleGrantResponse response = roleGrantService.revokeRole(
                actorIdOpt.get(),
                request.targetIdentityId(),
                request.role(),
                request.scopeRef());
        return ResponseEntity.ok(response);
    }

    private Optional<UUID> resolveActorIdentityId(OidcUser oidcUser) {
        Optional<OidcClaimsProjection> claimsOpt = OidcClaimsProjection.from(oidcUser);
        if (claimsOpt.isEmpty()) {
            return Optional.empty();
        }
        OidcClaimsProjection claims = claimsOpt.get();
        LocalIdentity identity = localIdentityService.persistCurrentIdentity(claims.issuer(), claims.subject());
        return Optional.ofNullable(identity.getIdentityId());
    }
}
