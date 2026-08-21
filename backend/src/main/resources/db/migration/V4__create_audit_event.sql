CREATE TABLE public.audit_event (
    event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor_identity_id uuid NULL,
    actor_role varchar(32) COLLATE "C" NULL,
    target_identity_id uuid NULL,
    action varchar(64) COLLATE "C" NOT NULL,
    outcome varchar(16) COLLATE "C" NOT NULL,
    previous_value varchar(2048) COLLATE "C" NULL,
    new_value varchar(2048) COLLATE "C" NULL,
    reason varchar(1024) COLLATE "C" NULL,
    correlation_id varchar(64) COLLATE "C" NULL,
    idempotency_key varchar(128) COLLATE "C" NULL,

    CONSTRAINT pk_audit_event
        PRIMARY KEY (event_id),

    CONSTRAINT fk_audit_event_actor_identity
        FOREIGN KEY (actor_identity_id)
        REFERENCES public.local_identity (identity_id),

    CONSTRAINT fk_audit_event_target_identity
        FOREIGN KEY (target_identity_id)
        REFERENCES public.local_identity (identity_id),

    CONSTRAINT ck_audit_event_actor_role_not_empty
        CHECK (actor_role <> ''),

    CONSTRAINT ck_audit_event_actor_role_octets
        CHECK (octet_length(actor_role) <= 32),

    CONSTRAINT ck_audit_event_action_not_empty
        CHECK (action <> ''),

    CONSTRAINT ck_audit_event_action_octets
        CHECK (octet_length(action) <= 64),

    CONSTRAINT ck_audit_event_action_values
        CHECK (action IN (
            'PAPEL_CONCEDIDO',
            'PAPEL_REVOGADO',
            'AUTOCONCESSAO_PAPEL',
            'ULTIMO_ADMINISTRADOR_GERAL',
            'REVOGACAO_ULTIMO_ADMINISTRADOR',
            'REGISTRO_MATRICULA',
            'CORRECAO_MATRICULA',
            'LOGIN_EFETUADO',
            'CONSULTA_OPERACIONAL',
            'SEMENTE_ADMINISTRADOR_INICIAL'
        )),

    CONSTRAINT ck_audit_event_outcome_not_empty
        CHECK (outcome <> ''),

    CONSTRAINT ck_audit_event_outcome_octets
        CHECK (octet_length(outcome) <= 16),

    CONSTRAINT ck_audit_event_outcome_values
        CHECK (outcome IN ('PERMITIDO', 'BLOQUEADO')),

    CONSTRAINT ck_audit_event_previous_value_not_empty
        CHECK (previous_value <> ''),

    CONSTRAINT ck_audit_event_previous_value_octets
        CHECK (octet_length(previous_value) <= 2048),

    CONSTRAINT ck_audit_event_new_value_not_empty
        CHECK (new_value <> ''),

    CONSTRAINT ck_audit_event_new_value_octets
        CHECK (octet_length(new_value) <= 2048),

    CONSTRAINT ck_audit_event_reason_not_empty
        CHECK (reason <> ''),

    CONSTRAINT ck_audit_event_reason_octets
        CHECK (octet_length(reason) <= 1024),

    CONSTRAINT ck_audit_event_correlation_id_not_empty
        CHECK (correlation_id <> ''),

    CONSTRAINT ck_audit_event_correlation_id_octets
        CHECK (octet_length(correlation_id) <= 64),

    CONSTRAINT ck_audit_event_idempotency_key_not_empty
        CHECK (idempotency_key <> ''),

    CONSTRAINT ck_audit_event_idempotency_key_octets
        CHECK (octet_length(idempotency_key) <= 128)
);

CREATE UNIQUE INDEX uq_audit_event_idempotency_key
    ON public.audit_event (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE OR REPLACE FUNCTION public.prevent_audit_event_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_event_modification
BEFORE UPDATE OR DELETE ON public.audit_event
FOR EACH ROW
EXECUTE FUNCTION public.prevent_audit_event_modification();

DO $$
DECLARE
    runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured BETUFU runtime role does not exist';
    END IF;

    EXECUTE format(
        'REVOKE ALL PRIVILEGES ON TABLE public.audit_event FROM %I',
        runtime_role
    );
    EXECUTE format(
        'GRANT SELECT, INSERT ON TABLE public.audit_event TO %I',
        runtime_role
    );
END $$;
