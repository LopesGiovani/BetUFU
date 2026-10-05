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
@RequestMapping("/api/v1/chat/messages/{messageId}/reports")
public class ChatReportController {
  private final ChatReportService service;

  public ChatReportController(ChatReportService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ChatCommunityModels.ReportReceipt> report(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID messageId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    JsonNode value = ChatCommunityModels.onlyField(body, "reason");
    if (!value.isString()) throw ChatCommunityModels.invalid();
    ChatCommunityModels.ReportReason reason;
    try {
      reason = ChatCommunityModels.ReportReason.valueOf(value.asString());
    } catch (IllegalArgumentException invalidReason) {
      throw ChatCommunityModels.invalid();
    }
    var session = request.getSession(false);
    return ChatCommunityModels.response(
        HttpStatus.CREATED,
        service.report(
            user.getIssuer().toString(),
            user.getSubject(),
            session == null ? null : session.getId(),
            messageId,
            ChatCommunityModels.key(key),
            reason));
  }
}
