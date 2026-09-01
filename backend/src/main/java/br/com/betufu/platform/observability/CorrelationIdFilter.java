package br.com.betufu.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that installs a validated {@code correlationId} into the SLF4J MDC before any
 * other processing, and removes it unconditionally in {@code finally} after the response completes.
 * The same value is echoed back as the {@code X-Correlation-ID} response header.
 *
 * <h2>Registration</h2>
 *
 * <p>Registered as a plain {@code @Component} Servlet filter with {@code @Order(HIGHEST_PRECEDENCE
 * + 10)}. This places it <strong>before</strong> the {@link
 * org.springframework.security.web.FilterChainProxy DelegatingFilterProxy} that delegates to Spring
 * Security, so that 401/403 responses produced in the security chain already have the correlation
 * ID available in the MDC (and therefore in log output).
 *
 * <h2>Validation</h2>
 *
 * <p>The incoming {@code X-Correlation-ID} header is accepted only if it consists of 1–64 ASCII
 * printable characters (U+0020–U+007E). An absent, empty, or invalid header causes a fresh {@link
 * UUID} to be generated. This prevents log‐injection via control characters, Unicode shenanigans,
 * or excessively long values.
 *
 * <h2>Thread safety</h2>
 *
 * <p>{@link MDC} is inherently thread‐local. The {@code finally} block guarantees that a finished
 * request never leaks its correlation ID into a recycled thread's MDC, satisfying the {@code [EDGE
 * OPS-03/adjacency]} and {@code [EDGE OPS-03/concurrency]} requirements.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /** HTTP header name used to send and receive the correlation ID. */
  static final String HEADER_NAME = "X-Correlation-ID";

  /** MDC key — matches the key read by {@code ProblemDetailsHandler.enrich()}. */
  static final String MDC_KEY = "correlationId";

  /** Maximum accepted length for an incoming header value. */
  private static final int MAX_LENGTH = 64;

  /**
   * Matches 1–{@value MAX_LENGTH} ASCII printable characters (space U+0020 through tilde U+007E).
   * Anything outside this range — including control characters, high-byte UTF-8, or zero-width
   * joiners — triggers a fresh UUID.
   */
  private static final Pattern VALID_PATTERN =
      Pattern.compile("^[\\x20-\\x7E]{1," + MAX_LENGTH + "}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String correlationId = extractOrGenerate(request);
    MDC.put(MDC_KEY, correlationId);
    try {
      response.setHeader(HEADER_NAME, correlationId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** Returns the header value when valid, or a fresh UUID otherwise. */
  private String extractOrGenerate(HttpServletRequest request) {
    String header = request.getHeader(HEADER_NAME);
    if (header != null && !header.isEmpty() && VALID_PATTERN.matcher(header).matches()) {
      return header;
    }
    return UUID.randomUUID().toString();
  }
}
