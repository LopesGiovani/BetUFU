package br.com.betufu.chat.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/chat/rules/preference")
public class ChatRulesPreferenceController {
  private final ChatRulesPreferenceService service;

  public ChatRulesPreferenceController(ChatRulesPreferenceService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<Dismissed> read(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    requireUser(user);
    return response(
        new Dismissed(
            service.read(user.getIssuer().toString(), user.getSubject(), session(request))),
        HttpStatus.OK);
  }

  @PostMapping("/dismiss")
  public ResponseEntity<Dismissed> dismiss(
      @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
    requireUser(user);
    return response(
        new Dismissed(
            service.dismissForSession(
                user.getIssuer().toString(), user.getSubject(), session(request))),
        HttpStatus.OK);
  }

  private static void requireUser(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static String session(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  private static <T> ResponseEntity<T> response(T body, HttpStatus status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, "Cookie")
        .body(body);
  }

  public record Dismissed(boolean dismissed) {}
}
