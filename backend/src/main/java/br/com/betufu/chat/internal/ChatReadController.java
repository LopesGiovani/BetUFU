package br.com.betufu.chat.internal;

import br.com.betufu.chat.internal.ChatReadModels.ChatReadResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/chat")
public class ChatReadController {
  private final ChatReadService service;

  public ChatReadController(ChatReadService service) {
    this.service = service;
  }

  @GetMapping("/events/{eventId}")
  public ResponseEntity<ChatReadResponse> event(
      @PathVariable UUID eventId,
      @RequestParam(required = false) String historyCursor,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal OidcUser user) {
    return response(service.read(eventId, user != null, historyCursor, null, limit), false);
  }

  @GetMapping("/events/{eventId}/changes")
  public ResponseEntity<ChatReadResponse> eventChanges(
      @PathVariable UUID eventId,
      @RequestParam(defaultValue = "0") long changeCursor,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal OidcUser user) {
    return response(service.read(eventId, user != null, null, changeCursor, limit), false);
  }

  @GetMapping("/global")
  public ResponseEntity<ChatReadResponse> global(
      @RequestParam(required = false) String historyCursor,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal OidcUser user) {
    return response(service.read(null, user != null, historyCursor, null, limit), true);
  }

  @GetMapping("/global/changes")
  public ResponseEntity<ChatReadResponse> globalChanges(
      @RequestParam(defaultValue = "0") long changeCursor,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal OidcUser user) {
    return response(service.read(null, user != null, null, changeCursor, limit), true);
  }

  @GetMapping("/events/{eventId}/availability")
  public ResponseEntity<ChatReadModels.Availability> eventAvailability(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal OidcUser user,
      HttpServletRequest request) {
    return ResponseEntity.ok(
        service.availability(eventId, user != null, issuer(user), subject(user), session(request)));
  }

  @GetMapping("/global/availability")
  public ResponseEntity<ChatReadModels.Availability> globalAvailability(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    return ResponseEntity.ok(
        service.availability(null, user != null, issuer(user), subject(user), session(request)));
  }

  private static String issuer(OidcUser user) {
    return user == null ? null : user.getIssuer().toString();
  }

  private static String subject(OidcUser user) {
    return user == null ? null : user.getSubject();
  }

  private static String session(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  private ResponseEntity<ChatReadResponse> response(ChatReadResponse body, boolean global) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, global ? "private, no-store" : "no-store")
        .header(HttpHeaders.VARY, "Cookie")
        .body(body);
  }
}
