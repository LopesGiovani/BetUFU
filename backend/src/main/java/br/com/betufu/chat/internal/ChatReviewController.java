package br.com.betufu.chat.internal;

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
public class ChatReviewController {
  private final ChatReviewService service;

  public ChatReviewController(ChatReviewService service) {
    this.service = service;
  }

  @PostMapping("/me/sanctions/{sanctionId}/reviews")
  public ResponseEntity<ChatModerationModels.ReviewReceipt> request(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID sanctionId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    ChatSanctionController.requireUser(user);
    ChatSanctionController.fields(body, "clientCommandId", "reason");
    try {
      var command =
          new ChatModerationModels.Command(
              ChatModerationModels.uuid(body.get("clientCommandId").asString()),
              ChatModerationModels.reason(body.get("reason").asString()));
      return ChatSanctionController.response(
          service.request(
              user.getIssuer().toString(),
              user.getSubject(),
              ChatSanctionController.session(request),
              sanctionId,
              command),
          HttpStatus.CREATED);
    } catch (IllegalArgumentException error) {
      throw ChatCommunityModels.invalid();
    }
  }

  @GetMapping("/moderation/reviews")
  public ResponseEntity<List<ChatModerationModels.ReviewReceipt>> pending(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    ChatSanctionController.requireUser(user);
    return ChatSanctionController.response(
        service.pending(
            user.getIssuer().toString(),
            user.getSubject(),
            ChatSanctionController.session(request)),
        HttpStatus.OK);
  }

  @PostMapping("/moderation/reviews/{reviewId}/decisions")
  public ResponseEntity<ChatModerationModels.ReviewReceipt> decide(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID reviewId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    ChatSanctionController.requireUser(user);
    ChatSanctionController.fields(body, "clientCommandId", "reason", "decision");
    try {
      var command =
          new ChatModerationModels.ReviewCommand(
              ChatModerationModels.uuid(body.get("clientCommandId").asString()),
              ChatModerationModels.reason(body.get("reason").asString()),
              ChatModerationModels.ReviewDecision.valueOf(body.get("decision").asString()));
      return ChatSanctionController.response(
          service.decide(
              user.getIssuer().toString(),
              user.getSubject(),
              ChatSanctionController.session(request),
              reviewId,
              command),
          HttpStatus.OK);
    } catch (IllegalArgumentException error) {
      throw ChatCommunityModels.invalid();
    }
  }
}
