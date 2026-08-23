package br.com.betufu.identity.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.betufu.identity.OperationalRole;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Access(AccessType.FIELD)
@Table(name = "operational_role_grant", schema = "public")
public class OperationalRoleGrant {

    public static final String STATUS_VIGENTE = "VIGENTE";
    public static final String STATUS_REVOGADO = "REVOGADO";

    @Id
    @Column(name = "grant_id", nullable = false, updatable = false)
    private UUID grantId;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private UUID identityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 32)
    private OperationalRole roleName;

    @Column(name = "scope_ref")
    private UUID scopeRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(
            name = "granted_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamptz(6)")
    private Instant grantedAt;

    @Column(
            name = "revoked_at",
            columnDefinition = "timestamptz(6)")
    private Instant revokedAt;

    protected OperationalRoleGrant() {}

    public OperationalRoleGrant(
            UUID grantId,
            UUID identityId,
            OperationalRole roleName,
            UUID scopeRef,
            String status,
            Instant grantedAt,
            Instant revokedAt) {
        this.grantId = Objects.requireNonNull(grantId, "grantId must not be null");
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.scopeRef = scopeRef;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt must not be null");
        this.revokedAt = revokedAt;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public OperationalRole getRoleName() {
        return roleName;
    }

    public UUID getScopeRef() {
        return scopeRef;
    }

    public String getStatus() {
        return status;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isVigente() {
        return STATUS_VIGENTE.equals(status);
    }

    public void revoke(Instant when) {
        this.status = STATUS_REVOGADO;
        this.revokedAt = Objects.requireNonNull(when, "revokedAt must not be null");
    }
}
