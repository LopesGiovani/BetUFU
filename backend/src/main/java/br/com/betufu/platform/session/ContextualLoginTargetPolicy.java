package br.com.betufu.platform.session;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates a same-origin application path without normalizing ambiguous input. */
public final class ContextualLoginTargetPolicy {

  private static final int MAX_TARGET_LENGTH = 2048;

  private ContextualLoginTargetPolicy() {}

  public static String requireSafeTarget(String candidate) {
    if (candidate == null
        || candidate.isBlank()
        || candidate.length() > MAX_TARGET_LENGTH
        || containsControlCharacter(candidate)
        || candidate.indexOf('\\') >= 0) {
      throw unsafeTarget();
    }

    URI target;
    try {
      target = new URI(candidate);
    } catch (URISyntaxException exception) {
      throw unsafeTarget();
    }

    String rawPath = target.getRawPath();
    String path = target.getPath();
    if (target.isAbsolute()
        || target.isOpaque()
        || target.getRawAuthority() != null
        || rawPath == null
        || path == null
        || !rawPath.startsWith("/")
        || rawPath.startsWith("//")
        || path.startsWith("//")
        || path.contains("//")
        || path.indexOf('\\') >= 0
        || containsControlCharacter(path)
        || containsControlCharacter(target.getQuery())
        || containsControlCharacter(target.getFragment())
        || hasDotSegment(path)
        || isTechnicalPath(path)) {
      throw unsafeTarget();
    }
    return candidate;
  }

  private static boolean containsControlCharacter(String value) {
    if (value == null) return false;
    return value.chars().anyMatch(character -> character <= 0x1F || character == 0x7F);
  }

  private static boolean hasDotSegment(String path) {
    for (String segment : path.split("/", -1)) {
      if (segment.equals(".") || segment.equals("..")) return true;
    }
    return false;
  }

  private static boolean isTechnicalPath(String path) {
    String normalized = path.toLowerCase(Locale.ROOT);
    return hasPrefix(normalized, "/login")
        || hasPrefix(normalized, "/logout")
        || hasPrefix(normalized, "/oauth2")
        || hasPrefix(normalized, "/api")
        || hasPrefix(normalized, "/actuator")
        || hasPrefix(normalized, "/error")
        || hasPrefix(normalized, "/ws");
  }

  private static boolean hasPrefix(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  private static IllegalArgumentException unsafeTarget() {
    return new IllegalArgumentException("Unsafe contextual login target");
  }
}
