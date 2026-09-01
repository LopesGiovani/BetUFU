package br.com.betufu.platform.diagnostic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete immutable diagnostic response model per D-03 / D-04. */
public record DiagnosticResponse(
    DiagnosticStatus status,
    String summary,
    String correlationId,
    Instant timestamp,
    List<DiagnosticComponent> components,
    String environment,
    String version) {
  public DiagnosticResponse {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(summary, "summary must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(components, "components must not be null");
    components = List.copyOf(components);
    if (components.size() != 6) {
      throw new IllegalArgumentException(
          "Diagnostic response must contain exactly 6 components, got: " + components.size());
    }
  }
}
