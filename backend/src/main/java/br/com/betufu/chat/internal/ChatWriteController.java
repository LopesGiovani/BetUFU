package br.com.betufu.chat.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/chat")
public class ChatWriteController {
  private final ChatWriteService service;

  public ChatWriteController(ChatWriteService service) {
    this.service = service;
  }

  @PostMapping("/global/messages")
  public ResponseEntity<Ack> global(
      @AuthenticationPrincipal OidcUser user,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    return send(user, null, key, body, request);
  }

  @PostMapping("/events/{eventId}/messages")
  public ResponseEntity<Ack> event(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID eventId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    return send(user, eventId, key, body, request);
  }

  private ResponseEntity<Ack> send(
      OidcUser user, UUID event, String key, JsonNode body, HttpServletRequest request) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    if (!body.isObject() || body.size() != 1 || !body.has("text") || !body.get("text").isString())
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    UUID parsed;
    try {
      parsed = UUID.fromString(key);
      if (!parsed.toString().equals(key)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException badKey) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }
    var session = request.getSession(false);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, "Cookie")
        .body(
            service.write(
                user.getIssuer().toString(),
                user.getSubject(),
                session == null ? null : session.getId(),
                event,
                parsed,
                body.get("text").asString()));
  }

  @ExceptionHandler(ChatWriteService.RateLimited.class)
  ResponseEntity<ProblemDetail> limited(ChatWriteService.RateLimited limit) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
    problem.setTitle("Intervalo entre mensagens");
    problem.setDetail("Aguarde tres segundos entre novas mensagens.");
    problem.setProperty("code", "BETUFU-429-CHAT_RATE_LIMIT");
    problem.setProperty("action", "Aguarde o horario indicado e tente novamente.");
    problem.setProperty("retryAt", limit.retryAt);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.RETRY_AFTER, Long.toString(limit.retryAfter))
        .body(problem);
  }

  public record Ack(UUID messageId, Instant postedAt, long changeCursor) {}
}
