package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEligibilityPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

public final class ChatCommunityModels {
  private ChatCommunityModels() {}

  public enum ReportReason {
    DISCURSO_DE_ODIO,
    SPAM,
    OFENSA,
    ASSEDIO,
    FRAUDE
  }

  public record ReportReceipt(UUID reportId, Instant reportedAt) {}

  public record MuteState(long version, List<UUID> participantIds) {
    public MuteState {
      participantIds = List.copyOf(participantIds);
    }
  }

  static JsonNode onlyField(JsonNode body, String field) {
    if (body == null || !body.isObject() || body.size() != 1 || !body.has(field)) throw invalid();
    return body.get(field);
  }

  static UUID key(String value) {
    try {
      UUID parsed = UUID.fromString(value);
      if (!parsed.toString().equals(value)) throw invalid();
      return parsed;
    } catch (IllegalArgumentException | NullPointerException badKey) {
      throw invalid();
    }
  }

  static long version(String value) {
    if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException invalidVersion) {
      throw invalid();
    }
  }

  static ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }

  static ApiProblemException missing() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  static <T> ResponseEntity<T> response(HttpStatus status, T body) {
    var response =
        ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, "Cookie");
    if (body instanceof MuteState state) response.eTag("\"" + state.version() + "\"");
    return response.body(body);
  }

  @Component
  @Profile("runtime | integrated")
  public static class Access {
    private final ChatEligibilityPort eligibility;
    private final ObjectProvider<SessionRepository<?>> sessions;
    private final Clock clock;

    public Access(
        ChatEligibilityPort eligibility,
        ObjectProvider<SessionRepository<?>> sessions,
        Clock clock) {
      this.eligibility = eligibility;
      this.sessions = sessions;
      this.clock = clock;
    }

    UUID actor(String issuer, String subject, String sessionId) {
      requireCurrent(issuer, subject, sessionId);
      return eligibility
          .findActor(issuer, subject)
          .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }

    void requireCurrent(String issuer, String subject, String sessionId) {
      var repository = sessions.getIfAvailable();
      var session = repository == null || sessionId == null ? null : repository.findById(sessionId);
      SecurityContext context =
          session == null ? null : session.getAttribute("SPRING_SECURITY_CONTEXT");
      var auth = context == null ? null : context.getAuthentication();
      if (session == null
          || session.isExpired()
          || !clock
              .instant()
              .isBefore(session.getLastAccessedTime().plus(session.getMaxInactiveInterval()))
          || auth == null
          || !auth.isAuthenticated()
          || !(auth.getPrincipal() instanceof OidcUser user)
          || !issuer.equals(user.getIssuer().toString())
          || !subject.equals(user.getSubject()))
        throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
  }
}
