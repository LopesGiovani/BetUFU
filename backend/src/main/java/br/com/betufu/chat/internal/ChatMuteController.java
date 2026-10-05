package br.com.betufu.chat.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/chat/me/mutes")
public class ChatMuteController {
  private final ChatMuteService service;

  public ChatMuteController(ChatMuteService service) {
    this.service = service;
  }

  @GetMapping({"", "/{participantId}"})
  public ResponseEntity<ChatCommunityModels.MuteState> read(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable(required = false) UUID participantId,
      HttpServletRequest request) {
    requireUser(user);
    return ChatCommunityModels.response(
        HttpStatus.OK,
        service.read(user.getIssuer().toString(), user.getSubject(), sessionId(request)));
  }

  @PutMapping("/{participantId}")
  public ResponseEntity<ChatCommunityModels.MuteState> put(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID participantId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    return change(
        user,
        participantId,
        ChatCommunityModels.version(
            ChatCommunityModels.onlyField(body, "expectedVersion").toString()),
        true,
        request);
  }

  @DeleteMapping("/{participantId}")
  public ResponseEntity<ChatCommunityModels.MuteState> delete(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID participantId,
      @RequestHeader("If-Match") String version,
      HttpServletRequest request) {
    return change(user, participantId, matchingVersion(version), false, request);
  }

  private ResponseEntity<ChatCommunityModels.MuteState> change(
      OidcUser user, UUID target, long version, boolean muted, HttpServletRequest request) {
    requireUser(user);
    return ChatCommunityModels.response(
        HttpStatus.OK,
        service.change(
            user.getIssuer().toString(),
            user.getSubject(),
            sessionId(request),
            target,
            version,
            muted));
  }

  private void requireUser(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private long matchingVersion(String value) {
    if (value == null || value.length() < 3 || !value.startsWith("\"") || !value.endsWith("\""))
      throw ChatCommunityModels.invalid();
    return ChatCommunityModels.version(value.substring(1, value.length() - 1));
  }

  private String sessionId(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }
}
