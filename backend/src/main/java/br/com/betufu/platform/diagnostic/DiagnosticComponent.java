package br.com.betufu.platform.diagnostic;

import java.util.Objects;
import java.util.Set;

/**
 * Represents the health status projection of a single component in the diagnostic dashboard per
 * D-03.
 */
public record DiagnosticComponent(String name, DiagnosticStatus status, String description) {
  public static final String API = "API";
  public static final String BFF_SESSION = "Sessão BFF";
  public static final String KEYCLOAK = "Keycloak";
  public static final String POSTGRESQL = "PostgreSQL";
  public static final String REDIS = "Redis";
  public static final String OBJECT_STORAGE = "Armazenamento de objetos";

  public static final Set<String> ALLOWED_NAMES =
      Set.of(API, BFF_SESSION, KEYCLOAK, POSTGRESQL, REDIS, OBJECT_STORAGE);

  public DiagnosticComponent {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(description, "description must not be null");
    if (!ALLOWED_NAMES.contains(name)) {
      throw new IllegalArgumentException("Component name is not in allowlist: " + name);
    }
  }
}
