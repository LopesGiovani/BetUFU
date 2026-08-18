package br.com.betufu.platform.diagnostic;

/**
 * Functional contract for individual component health and dependency probes per D-03 / D-04.
 */
@FunctionalInterface
public interface DependencyProbe {

    /**
     * Executes the probe for a component.
     * Implementations must be resilient, time-bounded, and never leak internal topology or credentials.
     *
     * @return the diagnostic component status projection
     */
    DiagnosticComponent probe();
}
