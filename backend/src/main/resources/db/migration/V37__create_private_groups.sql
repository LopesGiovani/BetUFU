CREATE TABLE public.private_group (
    group_id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(80) NOT NULL,
    owner_identity_id uuid NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at timestamptz(6),
    CONSTRAINT pk_private_group PRIMARY KEY (group_id),
    CONSTRAINT fk_private_group_owner FOREIGN KEY (owner_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_private_group_name CHECK (length(btrim(name)) BETWEEN 1 AND 80),
    CONSTRAINT ck_private_group_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_private_group_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE TABLE public.private_group_membership (
    membership_id uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL,
    identity_id uuid NOT NULL,
    role varchar(16) COLLATE "C" NOT NULL,
    joined_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    left_at timestamptz(6),
    CONSTRAINT pk_private_group_membership PRIMARY KEY (membership_id),
    CONSTRAINT fk_private_group_membership_group FOREIGN KEY (group_id)
        REFERENCES public.private_group (group_id),
    CONSTRAINT fk_private_group_membership_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_private_group_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_private_group_membership_dates CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE UNIQUE INDEX uq_private_group_active_member
    ON public.private_group_membership (group_id, identity_id) WHERE left_at IS NULL;
CREATE UNIQUE INDEX uq_private_group_active_owner
    ON public.private_group_membership (group_id) WHERE role = 'OWNER' AND left_at IS NULL;
CREATE INDEX idx_private_group_membership_identity
    ON public.private_group_membership (identity_id, group_id) WHERE left_at IS NULL;

CREATE TABLE public.private_group_invitation (
    invitation_id uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    created_by_identity_id uuid NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at timestamptz(6) NOT NULL,
    used_by_identity_id uuid,
    used_at timestamptz(6),
    revoked_at timestamptz(6),
    CONSTRAINT pk_private_group_invitation PRIMARY KEY (invitation_id),
    CONSTRAINT fk_private_group_invitation_group FOREIGN KEY (group_id)
        REFERENCES public.private_group (group_id),
    CONSTRAINT fk_private_group_invitation_creator FOREIGN KEY (created_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_private_group_invitation_user FOREIGN KEY (used_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT uq_private_group_invitation_hash UNIQUE (token_hash),
    CONSTRAINT ck_private_group_invitation_hash CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_private_group_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_private_group_invitation_use CHECK ((used_by_identity_id IS NULL) = (used_at IS NULL)),
    CONSTRAINT ck_private_group_invitation_revoke CHECK (revoked_at IS NULL OR used_at IS NULL)
);

CREATE TABLE public.private_group_audit (
    event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL,
    event_type varchar(24) COLLATE "C" NOT NULL,
    actor_identity_id uuid NOT NULL,
    subject_identity_id uuid,
    event_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    detail varchar(80),
    CONSTRAINT pk_private_group_audit PRIMARY KEY (event_id),
    CONSTRAINT fk_private_group_audit_group FOREIGN KEY (group_id)
        REFERENCES public.private_group (group_id),
    CONSTRAINT fk_private_group_audit_actor FOREIGN KEY (actor_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_private_group_audit_subject FOREIGN KEY (subject_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_private_group_audit_type CHECK (event_type IN
        ('CREATED', 'RENAMED', 'INVITED', 'JOINED', 'PROMOTED', 'DEMOTED', 'REMOVED', 'LEFT', 'CLOSED'))
);

CREATE FUNCTION public.reject_private_group_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'private group audit is immutable';
END $$;

CREATE TRIGGER trg_private_group_audit_immutable
    BEFORE UPDATE OR DELETE ON public.private_group_audit
    FOR EACH ROW EXECUTE FUNCTION public.reject_private_group_audit_mutation();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.private_group, public.private_group_membership, public.private_group_invitation TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.private_group_audit TO %I', runtime_role);
END $$;
