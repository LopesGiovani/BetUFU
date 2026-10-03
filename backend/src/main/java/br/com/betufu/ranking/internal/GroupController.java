package br.com.betufu.ranking.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.ranking.GroupQueryPort;
import br.com.betufu.ranking.PrivateGroupPort;
import br.com.betufu.ranking.PrivateGroupPort.Invitation;
import br.com.betufu.ranking.PrivateGroupPort.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class GroupController {
  static final String GROUPS_PATH = "/api/v1/me/groups";
  static final String GROUP_PATH = "/api/v1/me/groups/{groupId}";
  static final String INVITATIONS_PATH = "/api/v1/me/groups/{groupId}/invitations";
  static final String JOIN_PATH = "/api/v1/me/groups/join";
  static final String MEMBERSHIP_PATH = "/api/v1/me/groups/{groupId}/memberships/{membershipId}";
  static final String LEAVE_PATH = "/api/v1/me/groups/{groupId}/leave";
  static final String CLOSE_PATH = "/api/v1/me/groups/{groupId}/close";

  private final GroupQueryPort queries;
  private final PrivateGroupPort commands;

  public GroupController(GroupQueryPort queries, PrivateGroupPort commands) {
    this.queries = queries;
    this.commands = commands;
  }

  @GetMapping(path = GROUPS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GroupSummaryPage> mine(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    Actor actor = actor(user);
    return ResponseEntity.ok(
        new GroupSummaryPage(queries.findMine(actor.issuer(), actor.subject())));
  }

  @GetMapping(path = GROUP_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GroupQueryPort.GroupView> group(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId) {
    Actor actor = actor(user);
    return queries
        .findGroup(actor.issuer(), actor.subject(), groupId)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND));
  }

  @PostMapping(
      path = GROUPS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GroupCreatedResponse> create(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody GroupNameRequest request) {
    Actor actor = actor(user);
    UUID groupId = commands.create(actor.issuer(), actor.subject(), request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(new GroupCreatedResponse(groupId));
  }

  @PatchMapping(path = GROUP_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> rename(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId,
      @Valid @RequestBody GroupNameRequest request) {
    Actor actor = actor(user);
    commands.rename(actor.issuer(), actor.subject(), groupId, request.name());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(
      path = INVITATIONS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Invitation> invite(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId,
      @Valid @RequestBody InvitationRequest request) {
    Actor actor = actor(user);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            commands.issueInvitation(
                actor.issuer(), actor.subject(), groupId, request.expiresAt()));
  }

  @PostMapping(
      path = JOIN_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GroupCreatedResponse> join(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @Valid @RequestBody JoinGroupRequest request) {
    Actor actor = actor(user);
    UUID groupId = commands.join(actor.issuer(), actor.subject(), request.code());
    return ResponseEntity.status(HttpStatus.CREATED).body(new GroupCreatedResponse(groupId));
  }

  @PatchMapping(path = MEMBERSHIP_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> changeRole(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId,
      @PathVariable UUID membershipId,
      @Valid @RequestBody GroupRoleRequest request) {
    Actor actor = actor(user);
    commands.changeRole(actor.issuer(), actor.subject(), groupId, membershipId, request.role());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(path = MEMBERSHIP_PATH)
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId,
      @PathVariable UUID membershipId) {
    Actor actor = actor(user);
    commands.remove(actor.issuer(), actor.subject(), groupId, membershipId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(path = LEAVE_PATH)
  public ResponseEntity<Void> leave(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId) {
    Actor actor = actor(user);
    commands.leave(actor.issuer(), actor.subject(), groupId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(path = CLOSE_PATH)
  public ResponseEntity<Void> close(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID groupId) {
    Actor actor = actor(user);
    commands.close(actor.issuer(), actor.subject(), groupId);
    return ResponseEntity.noContent().build();
  }

  private static Actor actor(OidcUser user) {
    if (user == null || user.getIssuer() == null || user.getSubject() == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return new Actor(user.getIssuer().toString(), user.getSubject());
  }

  record Actor(String issuer, String subject) {}

  @ExceptionHandler({
    SecurityException.class,
    java.util.NoSuchElementException.class,
    IllegalArgumentException.class,
    IllegalStateException.class
  })
  public ResponseEntity<org.springframework.http.ProblemDetail> rejection(
      RuntimeException failure) {
    HttpStatus status =
        failure instanceof SecurityException || failure instanceof java.util.NoSuchElementException
            ? HttpStatus.NOT_FOUND
            : failure instanceof IllegalArgumentException
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.CONFLICT;
    ApiErrorCode code =
        status == HttpStatus.CONFLICT
            ? ApiErrorCode.STALE_VERSION
            : ApiErrorCode.fromStatus(status.value());
    return new br.com.betufu.platform.api.ProblemDetailsHandler()
        .handleApiProblem(new ApiProblemException(status, code));
  }

  public record GroupSummaryPage(List<GroupQueryPort.GroupSummary> items) {
    public GroupSummaryPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record GroupNameRequest(@NotBlank @Size(max = 80) String name) {}

  public record InvitationRequest(@NotNull @Future Instant expiresAt) {}

  public record JoinGroupRequest(@NotBlank @Size(max = 64) String code) {}

  public record GroupRoleRequest(@NotNull Role role) {}

  public record GroupCreatedResponse(UUID groupId) {}
}
