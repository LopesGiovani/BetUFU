package br.com.betufu.platform.observability;

import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/**
 * Strips sensitive patterns from text before it reaches log output, response bodies, or any other
 * observable surface.
 *
 * <h2>Usage</h2>
 *
 * <p>Call {@link #sanitize(String)} on any string that might contain user-provided or
 * framework-provided data before logging it. The method is null-safe and idempotent.
 *
 * <h2>Patterns covered</h2>
 *
 * <ul>
 *   <li>{@code Authorization} and {@code Proxy-Authorization} header values
 *   <li>{@code Cookie} and {@code Set-Cookie} header values
 *   <li>JWT tokens (compact serialization starting with {@code eyJ})
 *   <li>Credential key–value pairs ({@code password=…}, {@code secret=…}, {@code client_secret=…},
 *       {@code api_key=…}, {@code apikey=…})
 *   <li>Unicode control characters (C0, DEL, C1) — prevents log injection
 * </ul>
 *
 * <p>The order of application matters: authorization headers are sanitized before the JWT pattern,
 * so a {@code Bearer eyJ…} token is replaced in one pass without leaving a partial JWT for the
 * second pattern to match.
 */
public final class LogSanitizer {

  private LogSanitizer() {
    // utility class
  }

  // ── Patterns (order of application matters) ───────────────────────────────

  /** {@code Authorization: Bearer …} or {@code Proxy-Authorization: …} */
  private static final Pattern AUTHORIZATION =
      Pattern.compile(
          "((?:Authorization|Proxy-Authorization)\\s*[:=]\\s*)\\S+", Pattern.CASE_INSENSITIVE);

  /** {@code Cookie: …} or {@code Set-Cookie: …} — value extends to end of token */
  private static final Pattern COOKIE =
      Pattern.compile("((?:Set-)?Cookie\\s*[:=]\\s*)[^\\n]+", Pattern.CASE_INSENSITIVE);

  /**
   * JWT compact serialization: header always starts with {@code eyJ} (base64url for
   * {@code {"}) followed by at least 10 base64url characters and optional dot-separated
   * segments.
   */
  private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}(?:\\.[A-Za-z0-9_-]+)*");

  /** Common credential key–value pairs: {@code password=…}, {@code secret=…}, etc. */
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "((?:password|passwd|pwd|secret|client_secret|api[_-]?key)\\s*[:=]\\s*)\\S+",
          Pattern.CASE_INSENSITIVE);

  /** Unicode control characters: C0 (U+0000–U+001F), DEL (U+007F), C1 (U+0080–U+009F). */
  private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cc}");

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Replaces sensitive patterns with safe placeholders and strips control characters.
   *
   * @param text the text to sanitize, may be {@code null}
   * @return sanitized text, or {@code null} if the input was {@code null}
   */
  public static @Nullable String sanitize(@Nullable String text) {
    if (text == null) {
      return null;
    }
    String result = text;
    result = AUTHORIZATION.matcher(result).replaceAll("$1[REDACTED]");
    result = COOKIE.matcher(result).replaceAll("$1[REDACTED]");
    result = JWT.matcher(result).replaceAll("[JWT-REDACTED]");
    result = CREDENTIAL.matcher(result).replaceAll("$1[REDACTED]");
    result = CONTROL_CHARS.matcher(result).replaceAll("");
    return result;
  }
}
