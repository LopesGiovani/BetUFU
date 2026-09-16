package br.com.betufu.platform.session;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public final class ContextualLoginController {

  private static final URI OIDC_AUTHORIZATION_ENDPOINT =
      URI.create("/oauth2/authorization/keycloak");

  @GetMapping("/login/start")
  ResponseEntity<Void> start(
      @RequestParam(name = "returnTo") String returnTo, HttpServletRequest request) {
    final String validatedTarget;
    try {
      validatedTarget = ContextualLoginTargetPolicy.requireSafeTarget(returnTo);
    } catch (IllegalArgumentException exception) {
      return ResponseEntity.badRequest().build();
    }

    request
        .getSession(true)
        .setAttribute(SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE, validatedTarget);
    return ResponseEntity.status(HttpStatus.FOUND).location(OIDC_AUTHORIZATION_ENDPOINT).build();
  }
}
