package br.com.betufu.chat.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
@RequestMapping("/api/v1/chat")
public class ChatSanctionController {
  private final ChatSanctionService service;

  public ChatSanctionController(ChatSanctionService service) {
    this.service = service;
  }

  @GetMapping("/me/sanctions")
  public ResponseEntity<List<ChatModerationModels.SanctionSummary>> own(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    requireUser(user);
    return response(
        service.own(user.getIssuer().toString(), user.getSubject(), session(request)),
        HttpStatus.OK);
  }

  @GetMapping("/moderation/cases/{caseId}/sanctions/suggestion")
  public ResponseEntity<ChatModerationModels.SanctionSuggestion> suggestion(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      HttpServletRequest request) {
    requireUser(user);
    return response(
        service.suggest(user.getIssuer().toString(), user.getSubject(), session(request), caseId),
        HttpStatus.OK);
  }

  @PostMapping("/moderation/cases/{caseId}/sanctions")
  public ResponseEntity<ChatModerationModels.SanctionSummary> confirm(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    fields(body, "clientCommandId", "reason", "kind");
    try {
      var command =
          new ChatModerationModels.SanctionCommand(
              ChatModerationModels.uuid(body.get("clientCommandId").asString()),
              ChatModerationModels.reason(body.get("reason").asString()),
              ChatModerationModels.SanctionKind.valueOf(body.get("kind").asString()));
      return response(
          service.confirm(
              user.getIssuer().toString(), user.getSubject(), session(request), caseId, command),
          HttpStatus.CREATED);
    } catch (IllegalArgumentException error) {
      throw ChatCommunityModels.invalid();
    }
  }

  static void fields(JsonNode body, String... fields) {
    if (body == null || !body.isObject() || body.size() != fields.length)
      throw ChatCommunityModels.invalid();
    for (String field : fields)
      if (!body.has(field) || !body.get(field).isString()) throw ChatCommunityModels.invalid();
  }

  static void requireUser(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  static String session(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  static <T> ResponseEntity<T> response(T body, HttpStatus status) {
    return ResponseEntity.status(status)
        .header("Cache-Control", "private, no-store")
        .header("Vary", "Cookie")
        .body(body);
  }
}
