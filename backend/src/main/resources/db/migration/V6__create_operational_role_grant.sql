CREATE TABLE public.operational_role_grant (
    grant_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    role_name varchar(32) COLLATE "C" NOT NULL,
    scope_ref uuid NULL,
    status varchar(16) COLLATE "C" NOT NULL,
    granted_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at timestamptz(6) NULL,

    CONSTRAINT pk_operational_role_grant
        PRIMARY KEY (grant_id),

    CONSTRAINT fk_operational_role_grant_identity
        FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),

    CONSTRAINT ck_operational_role_grant_role_name_not_empty
        CHECK (role_name <> ''),

    CONSTRAINT ck_operational_role_grant_role_name_octets
        CHECK (octet_length(role_name) <= 32),

    CONSTRAINT ck_operational_role_grant_role_name_values
        CHECK (role_name IN ('ADMINISTRADOR_GERAL', 'ADMINISTRADOR_COMPETICAO', 'MODERADOR')),

    CONSTRAINT ck_operational_role_grant_status_not_empty
        CHECK (status <> ''),

    CONSTRAINT ck_operational_role_grant_status_octets
        CHECK (octet_length(status) <= 16),

    CONSTRAINT ck_operational_role_grant_status_values
        CHECK (status IN ('VIGENTE', 'REVOGADO')),

    CONSTRAINT ck_operational_role_grant_revocation_order
        CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE UNIQUE INDEX uq_operational_role_grant_active
    ON public.operational_role_grant (identity_id, role_name, scope_ref) NULLS NOT DISTINCT
    WHERE status = 'VIGENTE';
