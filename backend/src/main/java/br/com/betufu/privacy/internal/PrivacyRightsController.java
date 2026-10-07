package br.com.betufu.privacy.internal;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/privacy")
class PrivacyRightsController {
  private final PrivacyRightsService service;

  PrivacyRightsController(PrivacyRightsService service) {
    this.service = service;
  }

  @GetMapping("/me/cases")
  ResponseEntity<List<PrivacyRightsModels.CaseView>> ownCases(
      @AuthenticationPrincipal OidcUser user,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset,
      HttpServletRequest request) {
    requireUser(user);
    return response(
        HttpStatus.OK,
        service.ownCases(
            user.getIssuer().toString(), user.getSubject(), sessionId(request), limit, offset));
  }

  @PostMapping("/requests")
  ResponseEntity<PrivacyRightsModels.CaseReceipt> request(
      @AuthenticationPrincipal OidcUser user,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    fields(body, "subjectKind", "requestKind", "verifiedChannel", "subjectRef", "proofReference");
    var command =
        new PrivacyRightsModels.RequestCommand(
            enumValue(PrivacyRightsModels.SubjectKind.class, body.get("subjectKind")),
            uuidOrNull(body.get("subjectRef")),
            enumValue(PrivacyRightsModels.RequestKind.class, body.get("requestKind")),
            textOrNull(body.get("proofReference")),
            requiredText(body.get("verifiedChannel")));
    return response(
        HttpStatus.CREATED,
        service.request(
            user.getIssuer().toString(), user.getSubject(), sessionId(request), command));
  }

  @GetMapping("/requests/{caseId}")
  ResponseEntity<PrivacyRightsModels.CaseView> view(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      HttpServletRequest request) {
    requireUser(user);
    return response(
        HttpStatus.OK,
        service.view(user.getIssuer().toString(), user.getSubject(), sessionId(request), caseId));
  }

  @PostMapping("/requests/{caseId}/responses")
  ResponseEntity<PrivacyRightsModels.ResponseReceipt> respond(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireUser(user);
    fields(body, "decision", "responseText");
    return response(
        HttpStatus.OK,
        service.respond(
            user.getIssuer().toString(),
            user.getSubject(),
            sessionId(request),
            caseId,
            requiredText(body.get("decision")),
            requiredText(body.get("responseText"))));
  }

  private static String sessionId(HttpServletRequest request) {
    var session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  private static void requireUser(OidcUser user) {
    if (user == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static void fields(JsonNode body, String... names) {
    if (body == null || !body.isObject() || body.size() != names.length) throw invalid();
    for (String name : names) if (!body.has(name)) throw invalid();
  }

  private static String requiredText(JsonNode node) {
    if (node == null || !node.isString() || node.asString().isBlank()) throw invalid();
    return node.asString();
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isNull()) return null;
    return requiredText(node);
  }

  private static UUID uuidOrNull(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (!node.isString()) throw invalid();
    try {
      return UUID.fromString(node.asString());
    } catch (IllegalArgumentException invalidUuid) {
      throw invalid();
    }
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
    try {
      return Enum.valueOf(type, requiredText(node));
    } catch (IllegalArgumentException invalidValue) {
      throw invalid();
    }
  }

  private static ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, "Cookie")
        .body(body);
  }

  @PostMapping("/requests/{caseId}/exports")
  ResponseEntity<PrivacyRightsService.ExportArtifact> prepareExport(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      HttpServletRequest request) {
    requireUser(user);
    return response(
        HttpStatus.CREATED,
        service.prepareExport(
            user.getIssuer().toString(), user.getSubject(), sessionId(request), caseId));
  }

  @GetMapping("/requests/{caseId}/exports/latest")
  ResponseEntity<PrivacyRightsService.ExportArtifact> downloadExport(
      @AuthenticationPrincipal OidcUser user,
      @PathVariable UUID caseId,
      HttpServletRequest request) {
    requireUser(user);
    return response(
        HttpStatus.OK,
        service.downloadExport(
            user.getIssuer().toString(), user.getSubject(), sessionId(request), caseId));
  }
}
