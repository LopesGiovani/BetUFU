-- Privacy requests are append-only facts; current state is a rebuildable projection.
CREATE TABLE public.privacy_rights_case_fact (
    case_id uuid NOT NULL DEFAULT gen_random_uuid(),
    subject_kind varchar(24) COLLATE "C" NOT NULL,
    subject_ref uuid,
    owner_identity_id uuid REFERENCES public.local_identity (identity_id),
    request_kind varchar(24) COLLATE "C" NOT NULL,
    proof_reference text,
    verified_channel varchar(32) COLLATE "C" NOT NULL,
    requested_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_privacy_rights_case_fact PRIMARY KEY (case_id),
    CONSTRAINT ck_privacy_rights_case_subject CHECK (
        (subject_kind = 'ACCOUNT' AND owner_identity_id IS NOT NULL AND proof_reference IS NULL)
        OR (subject_kind = 'ATHLETE_WITHOUT_ACCOUNT' AND owner_identity_id IS NULL
            AND proof_reference IS NOT NULL AND btrim(proof_reference) <> '')
    ),
    CONSTRAINT ck_privacy_rights_case_kind CHECK (
        subject_kind IN ('ACCOUNT', 'ATHLETE_WITHOUT_ACCOUNT')),
    CONSTRAINT ck_privacy_rights_request_kind CHECK (
        request_kind IN ('NOTICE', 'ACCESS', 'CORRECTION', 'DELETION', 'OBJECTION', 'PSEUDONYMIZATION')),
    CONSTRAINT ck_privacy_rights_channel CHECK (
        verified_channel IN ('AUTHENTICATED_SESSION', 'VERIFIED_OPERATOR_CHANNEL'))
);

CREATE TABLE public.privacy_rights_case_event (
    event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.privacy_rights_case_fact (case_id),
    event_kind varchar(32) COLLATE "C" NOT NULL,
    actor_identity_id uuid REFERENCES public.local_identity (identity_id),
    event_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_privacy_rights_case_event PRIMARY KEY (event_id),
    CONSTRAINT ck_privacy_rights_event_kind CHECK (
        event_kind IN ('REQUESTED', 'IDENTITY_VERIFIED', 'ANALYZED', 'RESPONSE_SENT',
                       'EXPORT_READY', 'DISPOSITIONED', 'PSEUDONYMIZED', 'REJECTED')),
    CONSTRAINT ck_privacy_rights_event_payload CHECK (jsonb_typeof(event_payload) = 'object')
);

CREATE TABLE public.privacy_rights_export_fact (
    export_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.privacy_rights_case_fact (case_id),
    format varchar(8) COLLATE "C" NOT NULL,
    artifact_digest varchar(64) COLLATE "C" NOT NULL,
    verified_channel varchar(32) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_privacy_rights_export_fact PRIMARY KEY (export_id),
    CONSTRAINT ck_privacy_rights_export_format CHECK (format IN ('JSON', 'CSV')),
    CONSTRAINT ck_privacy_rights_export_digest CHECK (artifact_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_privacy_rights_export_channel CHECK (
        verified_channel IN ('AUTHENTICATED_SESSION', 'VERIFIED_OPERATOR_CHANNEL'))
);

CREATE TABLE public.privacy_rights_disposition_fact (
    disposition_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.privacy_rights_case_fact (case_id),
    data_class varchar(32) COLLATE "C" NOT NULL,
    disposition_kind varchar(24) COLLATE "C" NOT NULL,
    reason varchar(64) COLLATE "C" NOT NULL,
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_privacy_rights_disposition_fact PRIMARY KEY (disposition_id),
    CONSTRAINT ck_privacy_rights_disposition_class CHECK (
        data_class IN ('COMMON_MESSAGES', 'MODERATION_EVIDENCE', 'SANCTION_PROGRESSION',
                       'ATHLETE_WITHOUT_ACCOUNT', 'LEDGER', 'AUDIT', 'LOGS', 'BACKUPS')),
    CONSTRAINT ck_privacy_rights_disposition_kind CHECK (
        disposition_kind IN ('EXPIRED', 'PURGED_FROM_PROJECTION', 'PSEUDONYMIZED'))
);

CREATE TABLE public.privacy_rights_case_state (
    case_id uuid NOT NULL REFERENCES public.privacy_rights_case_fact (case_id),
    state varchar(24) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    latest_event_id uuid REFERENCES public.privacy_rights_case_event (event_id),
    export_id uuid REFERENCES public.privacy_rights_export_fact (export_id),
    rebuilt_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_privacy_rights_case_state PRIMARY KEY (case_id),
    CONSTRAINT ck_privacy_rights_state CHECK (
        state IN ('PENDING', 'VERIFIED', 'UNDER_REVIEW', 'RESPONDED', 'REJECTED', 'CLOSED'))
);

CREATE INDEX idx_privacy_rights_case_owner
    ON public.privacy_rights_case_fact (owner_identity_id, requested_at DESC, case_id DESC);
CREATE INDEX idx_privacy_rights_case_subject
    ON public.privacy_rights_case_fact (subject_kind, subject_ref, requested_at DESC, case_id DESC);
CREATE INDEX idx_privacy_rights_event_case
    ON public.privacy_rights_case_event (case_id, occurred_at, event_id);
CREATE INDEX idx_privacy_rights_state_pending
    ON public.privacy_rights_case_state (state, rebuilt_at, case_id);

CREATE FUNCTION public.prevent_privacy_rights_fact_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'privacy rights history is immutable: UPDATE and DELETE are prohibited';
END $$;

CREATE TRIGGER trg_privacy_rights_case_fact_immutable
BEFORE UPDATE OR DELETE ON public.privacy_rights_case_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();
CREATE TRIGGER trg_privacy_rights_case_event_immutable
BEFORE UPDATE OR DELETE ON public.privacy_rights_case_event
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();
CREATE TRIGGER trg_privacy_rights_export_fact_immutable
BEFORE UPDATE OR DELETE ON public.privacy_rights_export_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();
CREATE TRIGGER trg_privacy_rights_disposition_fact_immutable
BEFORE UPDATE OR DELETE ON public.privacy_rights_disposition_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('REVOKE ALL ON TABLE public.privacy_rights_case_fact, public.privacy_rights_case_event, public.privacy_rights_export_fact, public.privacy_rights_disposition_fact, public.privacy_rights_case_state FROM PUBLIC');
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.privacy_rights_case_fact, public.privacy_rights_case_event, public.privacy_rights_export_fact, public.privacy_rights_disposition_fact FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.privacy_rights_case_fact, public.privacy_rights_case_event, public.privacy_rights_export_fact, public.privacy_rights_disposition_fact TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.privacy_rights_case_state TO %I', runtime_role);
END $$;
