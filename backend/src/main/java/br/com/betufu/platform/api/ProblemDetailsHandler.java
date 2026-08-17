package br.com.betufu.platform.api;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central error handler for {@code /api/v1} — RFC 9457 Problem Details with BETUFU extensions.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} to intercept all Spring MVC exceptions and
 * enrich them with the BETUFU contract: a stable {@code code}, a Portuguese {@code action} hint,
 * an optional {@code correlationId} from the MDC, and ordered {@code fieldErrors} for validation.
 *
 * <p>This bean replaces the auto-configured {@code ProblemDetailsExceptionHandler} via
 * {@code @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)} in Spring Boot,
 * so there is exactly one handler — no ordering conflict.
 *
 * <p>The handler deliberately does <strong>not</strong> touch 401/403: those are produced by the
 * {@link br.com.betufu.platform.security.SecurityConfiguration} entry points and access-denied
 * handler, which run in the security filter chain before the DispatcherServlet. This separation
 * was ratified in the audit and will be unified in a future plan if needed.
 *
 * <p>No exception message, cause, stack trace, internal path, host name, or token is ever
 * included in the response body — per D-14.
 */
@RestControllerAdvice
public class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Matches all Unicode control characters (C0: U+0000–U+001F, DEL: U+007F, C1: U+0080–U+009F).
     * These are stripped from any text that reaches the response body, preventing log-injection
     * vectors and garbled display without altering legitimate Unicode (accents, CJK, emoji).
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cc}");

    // ── Validation (400) ──────────────────────────────────────────────────────

    /**
     * Overrides the parent's handler to add sorted {@code fieldErrors} and BETUFU extension
     * fields. Returns directly — does not go through {@link #handleExceptionInternal} — so
     * the enrichment is done explicitly here.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ApiErrorCode errorCode = ApiErrorCode.VALIDATION_FAILED;

        ProblemDetail problem = ProblemDetail.forStatus(status);
        enrich(problem, errorCode);

        List<FieldViolation> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(
                        sanitize(fe.getField()),
                        sanitize(fe.getDefaultMessage() != null
                                ? fe.getDefaultMessage()
                                : "Valor inválido")))
                .sorted()
                .toList();
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    // ── Catch-all (500) ───────────────────────────────────────────────────────

    /**
     * Catches any exception not handled by the parent's specific handlers (Spring MVC
     * exceptions) or by Spring Security (401/403). The exception message is never echoed
     * to the client — only the stable Portuguese detail from {@link ApiErrorCode}.
     *
     * <p>Explicitly rethrows {@link org.springframework.security.access.AccessDeniedException}
     * and {@link org.springframework.security.core.AuthenticationException} so that security
     * failures occurring inside controller methods (e.g., method-level authorization) are
     * handled by Spring Security rather than degraded into HTTP 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof org.springframework.security.access.AccessDeniedException
                || ex instanceof org.springframework.security.core.AuthenticationException) {
            throw ex;
        }

        if (logger.isErrorEnabled()) {
            logger.error("Unhandled exception", ex);
        }

        ApiErrorCode errorCode = ApiErrorCode.INTERNAL_ERROR;

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        enrich(problem, errorCode);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    // ── All other Spring MVC exceptions (405, 415, 406, etc.) ─────────────────

    /**
     * Intercept point for every Spring MVC exception handled by the parent class
     * (method not allowed, unsupported media type, missing parameter, type mismatch, etc.).
     *
     * <p>The parent creates the {@link ProblemDetail} and passes it here. We enrich it with
     * BETUFU fields and override the English detail/title with Portuguese equivalents.
     * The guard on {@code "code"} prevents double-enrichment if a subclass override
     * already called {@link #enrich} (currently only {@link #handleMethodArgumentNotValid}).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ResponseEntity<Object> response =
                super.handleExceptionInternal(ex, body, headers, statusCode, request);

        Object responseBody = response.getBody();
        if (responseBody instanceof ProblemDetail problem) {
            if (problem.getProperties() == null
                    || !problem.getProperties().containsKey("code")) {
                ApiErrorCode errorCode = ApiErrorCode.fromStatus(statusCode.value());
                enrich(problem, errorCode);
            }
        }

        return response;
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private void enrich(ProblemDetail problem, ApiErrorCode errorCode) {
        problem.setTitle(errorCode.title());
        problem.setDetail(sanitize(errorCode.detail()));
        problem.setProperty("code", errorCode.code());
        problem.setProperty("action", errorCode.action());

        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId != null) {
            problem.setProperty(CORRELATION_ID_KEY, correlationId);
        }
    }

    /**
     * Strips Unicode control characters (C0, DEL, C1) from text.
     *
     * <p>Valid Unicode (accents, CJK, emoji) is preserved; only characters in the
     * {@code Cc} general category are removed. Package-private for unit testability.
     */
    static String sanitize(@Nullable String text) {
        if (text == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(text).replaceAll("");
    }
}
