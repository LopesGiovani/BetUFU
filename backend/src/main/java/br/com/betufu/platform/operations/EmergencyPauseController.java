package br.com.betufu.platform.operations;

import br.com.betufu.platform.api.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/operations/emergency-pauses")
public class EmergencyPauseController {
  private final EmergencyPauseService service;

  public EmergencyPauseController(EmergencyPauseService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<EmergencyPausePort.Receipt>> list(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    requireUser(user);
    return response(
        service.list(user.getIssuer().toString(), user.getSubject(), session(request)),
        HttpStatus.OK);
  }

  @PostMapping
  public ResponseEntity<EmergencyPausePort.Receipt> command(
      @AuthenticationPrincipal OidcUser user,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    if (body == null || !body.isObject()) throw invalid();
    for (String field : List.of("clientCommandId", "kind", "targetId", "action", "reason"))
      if (!body.has(field) || !body.get(field).isString()) throw invalid();
    try {
      var action = EmergencyPausePort.Action.valueOf(body.get("action").asString());
      UUID resumed = null;
      if (action == EmergencyPausePort.Action.RESUME) {
        if (!body.has("resumedPauseId")
            || !body.get("resumedPauseId").isString()
            || body.size() != 6) throw invalid();
        resumed = uuid(body.get("resumedPauseId").asString());
      } else if (body.size() != 5) throw invalid();
      var command =
          new EmergencyPausePort.Command(
              uuid(body.get("clientCommandId").asString()),
              EmergencyPausePort.Kind.valueOf(body.get("kind").asString()),
              uuid(body.get("targetId").asString()),
              action,
              body.get("reason").asString(),
              resumed);
      return response(
          service.command(
              user.getIssuer().toString(), user.getSubject(), session(request), command),
          HttpStatus.CREATED);
    } catch (IllegalArgumentException error) {
      throw invalid();
    }
  }

  private static UUID uuid(String value) {
    UUID id = UUID.fromString(value);
    if (!id.toString().equals(value)) throw new IllegalArgumentException();
    return id;
  }

  private static void requireUser(OidcUser user) {
    if (user == null)
      throw EmergencyPauseService.problem(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static String session(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  private static <T> ResponseEntity<T> response(T body, HttpStatus status) {
    return ResponseEntity.status(status)
        .header("Cache-Control", "private, no-store")
        .header("Vary", "Cookie")
        .body(body);
  }

  private static RuntimeException invalid() {
    return EmergencyPauseService.problem(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }
}
