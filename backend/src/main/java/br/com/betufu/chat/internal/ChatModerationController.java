package br.com.betufu.chat.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/chat/moderation")
public class ChatModerationController {
  private final ChatModerationService service;

  public ChatModerationController(ChatModerationService service) {
    this.service = service;
  }

  @GetMapping("/cases")
  public ResponseEntity<List<ChatModerationModels.CaseSummary>> cases(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    return ResponseEntity.ok()
        .headers(noStore())
        .body(service.list(issuer(user), subject(user), session(request)));
  }

  @PostMapping("/cases")
  public ResponseEntity<ChatModerationModels.CaseReceipt> open(
      @AuthenticationPrincipal OidcUser user,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    if (body == null
        || !body.isObject()
        || body.size() != 1
        || !body.has("reportId")
        || !body.get("reportId").isString()) throw ChatCommunityModels.invalid();
    var receipt =
        service.open(
            issuer(user),
            subject(user),
            session(request),
            ChatModerationModels.uuid(body.get("reportId").asString()));
    return ResponseEntity.status(HttpStatus.CREATED).headers(noStore()).body(receipt);
  }

  @PostMapping("/cases/{caseId}/remove")
  public ResponseEntity<ChatModerationModels.CaseReceipt> remove(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    var receipt =
        service.remove(issuer(user), subject(user), session(request), caseId, command(body));
    return ResponseEntity.ok().headers(noStore()).body(receipt);
  }

  @PostMapping("/cases/{caseId}/forward")
  public ResponseEntity<ChatModerationModels.ForwardReceipt> forward(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    var receipt =
        service.forward(issuer(user), subject(user), session(request), caseId, command(body));
    return ResponseEntity.ok().headers(noStore()).body(receipt);
  }

  @GetMapping("/cases/{caseId}/evidence")
  public ResponseEntity<ChatModerationModels.ModerationEvidence> evidence(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      @RequestParam String reason,
      HttpServletRequest request) {
    requireUser(user);
    var evidence = service.evidence(issuer(user), subject(user), session(request), caseId, reason);
    return ResponseEntity.ok().headers(noStore()).body(evidence);
  }

  private ChatModerationModels.Command command(JsonNode body) {
    if (body == null
        || !body.isObject()
        || body.size() != 2
        || !body.has("clientCommandId")
        || !body.has("reason")
        || !body.get("clientCommandId").isString()
        || !body.get("reason").isString()) throw ChatCommunityModels.invalid();
    try {
      return new ChatModerationModels.Command(
          ChatModerationModels.uuid(body.get("clientCommandId").asString()),
          ChatModerationModels.reason(body.get("reason").asString()));
    } catch (IllegalArgumentException error) {
      throw ChatCommunityModels.invalid();
    }
  }

  private static void requireUser(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static String issuer(OidcUser user) {
    requireUser(user);
    return user.getIssuer().toString();
  }

  private static String subject(OidcUser user) {
    requireUser(user);
    return user.getSubject();
  }

  private static String session(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  private static HttpHeaders noStore() {
    var headers = new HttpHeaders();
    headers.setCacheControl("private, no-store");
    headers.add(HttpHeaders.VARY, "Cookie");
    return headers;
  }
}
