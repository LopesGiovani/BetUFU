package br.com.betufu.identity.internal;

import java.util.UUID;

import br.com.betufu.identity.OperationalRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record RoleGrantRequest(
        @NotNull(message = "Identificador público é obrigatório")
        UUID targetIdentityId,

        @NotNull(message = "Papel é obrigatório")
        OperationalRole role,

        UUID scopeRef) {

    public RoleGrantRequest(UUID targetIdentityId, OperationalRole role) {
        this(targetIdentityId, role, null);
    }

    /**
     * O escopo deixa de ser sempre opcional: papel escopado exige, papel global proíbe. A mesma
     * regra vale no serviço e como CHECK no banco — aqui ela apenas antecipa a recusa ao binding.
     */
    @AssertTrue(message = "Escopo é obrigatório para papéis escopados e proibido para o papel global")
    public boolean isScopeConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return role.requiresScope() ? scopeRef != null : scopeRef == null;
    }
}
