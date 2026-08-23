package br.com.betufu.identity;

/**
 * Closed vocabulary of operational roles for BETUFU (D-46).
 *
 * <p>Cada papel declara se é escopado. A distinção não é cosmética: um papel escopado concedido
 * sem escopo nasce valendo em todo lugar, e o papel global concedido "dentro de um escopo" dá a
 * impressão de autoridade limitada enquanto confere autoridade total. As duas combinações são
 * proibidas no serviço e no banco.</p>
 */
public enum OperationalRole {

    /** Autoridade global. Só existe sem escopo. */
    ADMINISTRADOR_GERAL(false),

    /** Autoridade limitada a uma competição. Exige escopo. */
    ADMINISTRADOR_COMPETICAO(true),

    /** Autoridade limitada a um espaço de moderação. Exige escopo. */
    MODERADOR(true);

    private final boolean scoped;

    OperationalRole(boolean scoped) {
        this.scoped = scoped;
    }

    /** {@code true} quando a concessão exige um {@code scopeRef} não nulo. */
    public boolean requiresScope() {
        return scoped;
    }

    /** {@code true} quando a concessão só é válida com {@code scopeRef} nulo. */
    public boolean isGlobal() {
        return !scoped;
    }
}
