package br.com.betufu.platform.diagnostic;

import br.com.betufu.platform.observability.LogSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Aggregates diagnostic component statuses into an immutable, safe response per D-03 / D-04. */
@Service
public class DiagnosticService {

  private static final String DEFAULT_VERSION = "0.1.0";
  private static final String MDC_CORRELATION_ID = "correlationId";

  private final Clock clock;
  private final Environment environment;

  public DiagnosticService(
      @Autowired(required = false) Clock clock,
      @Autowired(required = false) Environment environment) {
    this.clock = clock != null ? clock : Clock.systemUTC();
    this.environment = environment;
  }

  /**
   * Aggregates exactly six components into a safe, ordered DiagnosticResponse. Computes the overall
   * status: AVAILABLE only if all 6 are AVAILABLE; DEGRADED otherwise.
   */
  public DiagnosticResponse aggregate(List<DiagnosticComponent> rawComponents) {
    if (rawComponents == null || rawComponents.size() != 6) {
      throw new IllegalArgumentException(
          "Expected exactly 6 components to aggregate, got: "
              + (rawComponents == null ? 0 : rawComponents.size()));
    }

    // Enforce strict order per D-03: API, Sessão BFF, Keycloak, PostgreSQL, Redis, Armazenamento de
    // objetos
    List<DiagnosticComponent> orderedComponents = enforceOrderedComponents(rawComponents);

    DiagnosticStatus overallStatus = computeOverallStatus(orderedComponents);
    String summary = computeSummary(overallStatus, orderedComponents);

    String correlationId = resolveCorrelationId();
    Instant timestamp = Instant.now(clock);
    String envName = resolveEnvironment();
    String appVersion = resolveVersion();

    return new DiagnosticResponse(
        overallStatus, summary, correlationId, timestamp, orderedComponents, envName, appVersion);
  }

  private List<DiagnosticComponent> enforceOrderedComponents(List<DiagnosticComponent> input) {
    List<String> requiredOrder =
        List.of(
            DiagnosticComponent.API,
            DiagnosticComponent.BFF_SESSION,
            DiagnosticComponent.KEYCLOAK,
            DiagnosticComponent.POSTGRESQL,
            DiagnosticComponent.REDIS,
            DiagnosticComponent.OBJECT_STORAGE);

    List<DiagnosticComponent> ordered = new ArrayList<>(6);
    for (String expectedName : requiredOrder) {
      DiagnosticComponent found = null;
      for (DiagnosticComponent c : input) {
        if (expectedName.equals(c.name())) {
          found = c;
          break;
        }
      }
      if (found == null) {
        throw new IllegalArgumentException(
            "Missing required component in diagnostic list: " + expectedName);
      }
      ordered.add(found);
    }
    return List.copyOf(ordered);
  }

  private DiagnosticStatus computeOverallStatus(List<DiagnosticComponent> components) {
    boolean allAvailable = true;
    boolean postgresUnavailable = false;
    boolean keycloakUnavailable = false;
    long unavailableCount = 0;

    for (DiagnosticComponent c : components) {
      if (c.status() != DiagnosticStatus.AVAILABLE) {
        allAvailable = false;
      }
      if (c.status() == DiagnosticStatus.UNAVAILABLE) {
        unavailableCount++;
        if (DiagnosticComponent.POSTGRESQL.equals(c.name())) {
          postgresUnavailable = true;
        }
        if (DiagnosticComponent.KEYCLOAK.equals(c.name())) {
          keycloakUnavailable = true;
        }
      }
    }

    if (allAvailable) {
      return DiagnosticStatus.AVAILABLE;
    }

    // Regra semântica da BETUFU: PostgreSQL é a fonte autoritativa de dados (01-10) e Keycloak
    // é a autoridade de identidade. Sem banco, sem login, ou com 2+ dependências indisponíveis,
    // a plataforma fica globalmente UNAVAILABLE.
    if (postgresUnavailable || keycloakUnavailable || unavailableCount >= 2) {
      return DiagnosticStatus.UNAVAILABLE;
    }

    return DiagnosticStatus.DEGRADED;
  }

  private String computeSummary(
      DiagnosticStatus overallStatus, List<DiagnosticComponent> components) {
    if (overallStatus == DiagnosticStatus.AVAILABLE) {
      return "Todos os sistemas operacionais";
    }
    if (overallStatus == DiagnosticStatus.UNAVAILABLE) {
      long unavailableCount =
          components.stream().filter(c -> c.status() == DiagnosticStatus.UNAVAILABLE).count();
      return "Plataforma indisponível ("
          + unavailableCount
          + " dependência(s) crítica(s) fora do ar)";
    }
    long nonAvailableCount =
        components.stream().filter(c -> c.status() != DiagnosticStatus.AVAILABLE).count();
    return nonAvailableCount + " serviço(s) operando em modo degradado";
  }

  private String resolveCorrelationId() {
    String mdcValue = MDC.get(MDC_CORRELATION_ID);
    if (mdcValue != null && !mdcValue.isBlank()) {
      return LogSanitizer.sanitize(mdcValue);
    }
    return "untracked-" + UUID.randomUUID();
  }

  private String resolveEnvironment() {
    if (environment != null) {
      String[] activeProfiles = environment.getActiveProfiles();
      if (activeProfiles != null && activeProfiles.length > 0) {
        List<String> sorted = new ArrayList<>(List.of(activeProfiles));
        sorted.sort(String::compareTo);
        return String.join(",", sorted);
      }
    }
    return "default";
  }

  private String resolveVersion() {
    if (environment != null) {
      String versionProp = environment.getProperty("betufu.version");
      if (versionProp != null && !versionProp.isBlank()) {
        return versionProp;
      }
    }
    return DEFAULT_VERSION;
  }
}
