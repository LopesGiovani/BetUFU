package br.com.betufu.verification.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.api.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class EnrollmentController {

  private static final String ENROLLMENT_ME_PATH = "/api/v1/enrollment/me";
  private static final String STATUS_NAO_REGISTRADA = "NAO_REGISTRADA";

  private final EnrollmentService enrollmentService;
  private final EnrollmentAttemptLimiter attemptLimiter;
  private final JdbcTemplate jdbcTemplate;

  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentAttemptLimiter attemptLimiter,
      JdbcTemplate jdbcTemplate) {
    this.enrollmentService =
        Objects.requireNonNull(enrollmentService, "enrollmentService must not be null");
    this.attemptLimiter = Objects.requireNonNull(attemptLimiter, "attemptLimiter must not be null");
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @GetMapping(path = ENROLLMENT_ME_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<EnrollmentStatusResponse> getEnrollment(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    UUID identityId = resolveIdentityIdFromOidc(oidcUser);
    if (identityId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Optional<UfuEnrollment> active = enrollmentService.findActiveEnrollment(identityId);
    return active
        .map(
            enrollment ->
                ResponseEntity.ok(
                    new EnrollmentStatusResponse(
                        enrollment.getStatus(), enrollment.getCreatedAt())))
        .orElseGet(
            () -> ResponseEntity.ok(new EnrollmentStatusResponse(STATUS_NAO_REGISTRADA, null)));
  }

  @PutMapping(
      path = ENROLLMENT_ME_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<EnrollmentStatusResponse> putEnrollment(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      @Valid @RequestBody EnrollmentRegistrationRequest request,
      HttpServletRequest servletRequest) {
    UUID identityId = resolveIdentityIdFromOidc(oidcUser);
    if (identityId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    String originIp = servletRequest != null ? servletRequest.getRemoteAddr() : null;

    if (!attemptLimiter.tryAcquire(identityId, originIp)) {
      enrollmentService.auditRejection(identityId, "Limite de tentativas excedido");
      throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.TOO_MANY_ATTEMPTS);
    }

    try {
      EnrollmentStatusResponse response =
          enrollmentService.register(identityId, request.matricula());
      attemptLimiter.release(identityId, originIp);
      return ResponseEntity.ok(response);
    } catch (ApiProblemException e) {
      // Business rejection: keep reservation consumed in Redis
      throw e;
    } catch (RuntimeException | Error e) {
      // Unclassified exception / 500: release reservation before propagating
      attemptLimiter.release(identityId, originIp);
      throw e;
    }
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex,
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
      HttpServletRequest servletRequest) {
    String originIp = servletRequest != null ? servletRequest.getRemoteAddr() : null;
    UUID identityId = resolveIdentityIdFromOidc(oidcUser);

    if (!attemptLimiter.tryAcquire(identityId, originIp)) {
      if (identityId != null) {
        enrollmentService.auditRejection(identityId, "Limite de tentativas excedido");
      }
      ApiErrorCode errorCode = ApiErrorCode.TOO_MANY_ATTEMPTS;
      ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
      problem.setTitle(errorCode.title());
      problem.setDetail(errorCode.detail());
      problem.setProperty("code", errorCode.code());
      problem.setProperty("action", errorCode.action());

      String correlationId = MDC.get("correlationId");
      if (correlationId != null) {
        problem.setProperty("correlationId", correlationId);
      }

      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(problem);
    }

    try {
      if (identityId != null) {
        enrollmentService.auditRejection(identityId, "Formato de matrícula inválido");
      }

      ApiErrorCode errorCode = ApiErrorCode.VALIDATION_FAILED;
      ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
      problem.setTitle(errorCode.title());
      problem.setDetail(errorCode.detail());
      problem.setProperty("code", errorCode.code());
      problem.setProperty("action", errorCode.action());

      String correlationId = MDC.get("correlationId");
      if (correlationId != null) {
        problem.setProperty("correlationId", correlationId);
      }

      List<FieldViolation> fieldErrors =
          ex.getBindingResult().getFieldErrors().stream()
              .map(
                  fe ->
                      new FieldViolation(
                          fe.getField(),
                          fe.getDefaultMessage() != null
                              ? fe.getDefaultMessage()
                              : "Valor inválido"))
              .sorted()
              .toList();
      problem.setProperty("fieldErrors", fieldErrors);

      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(problem);
    } catch (RuntimeException e) {
      attemptLimiter.release(identityId, originIp);
      ApiErrorCode errorCode = ApiErrorCode.INTERNAL_ERROR;
      ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
      problem.setTitle(errorCode.title());
      problem.setDetail(errorCode.detail());
      problem.setProperty("code", errorCode.code());
      problem.setProperty("action", errorCode.action());

      String correlationId = MDC.get("correlationId");
      if (correlationId != null) {
        problem.setProperty("correlationId", correlationId);
      }

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(problem);
    } catch (Error e) {
      attemptLimiter.release(identityId, originIp);
      throw e;
    }
  }

  private UUID resolveIdentityIdFromOidc(OidcUser oidcUser) {
    if (oidcUser == null || oidcUser.getIdToken() == null) {
      return null;
    }
    Object issValue = oidcUser.getIdToken().getClaims().get(IdTokenClaimNames.ISS);
    String issuer =
        switch (issValue) {
          case String string -> string;
          case URL url -> url.toString();
          case URI uri -> uri.toString();
          case null, default -> null;
        };
    String subject = oidcUser.getIdToken().getSubject();
    if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
      return null;
    }
    return resolveIdentityId(issuer, subject);
  }

  private UUID resolveIdentityId(String issuer, String subject) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
        issuer,
        subject);
    List<UUID> list =
        jdbcTemplate.query(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            (rs, rowNum) -> rs.getObject("identity_id", UUID.class),
            issuer,
            subject);
    return list.isEmpty() ? null : list.getFirst();
  }
}
