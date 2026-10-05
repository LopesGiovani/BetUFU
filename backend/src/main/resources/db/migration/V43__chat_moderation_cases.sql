-- Moderation cases are rebuildable projections over immutable moderation facts.
CREATE TABLE public.chat_moderation_case (
    case_id uuid NOT NULL DEFAULT gen_random_uuid(),
    message_id uuid NOT NULL REFERENCES public.chat_message (message_id),
    channel_id uuid NOT NULL REFERENCES public.chat_channel (channel_id),
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'OPEN',
    opened_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_case PRIMARY KEY (case_id),
    CONSTRAINT uq_chat_moderation_case_message UNIQUE (message_id),
    CONSTRAINT ck_chat_moderation_case_status CHECK (
        status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'ESCALATED'))
);

CREATE INDEX idx_chat_moderation_case_channel_status
    ON public.chat_moderation_case (channel_id, status, opened_at, case_id);

-- Several reports of one message attach to one case occurrence.
CREATE TABLE public.chat_moderation_case_report_fact (
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    report_id uuid NOT NULL REFERENCES public.chat_report (report_id),
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_case_report_fact PRIMARY KEY (case_id, report_id),
    CONSTRAINT uq_chat_moderation_case_report_fact_report UNIQUE (report_id)
);

CREATE TABLE public.chat_moderation_review_fact (
    review_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    review_kind varchar(24) COLLATE "C" NOT NULL,
    reason varchar(280) NOT NULL,
    client_command_id uuid NOT NULL,
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_review_fact PRIMARY KEY (review_fact_id),
    CONSTRAINT uq_chat_moderation_review_fact_command UNIQUE (actor_identity_id, client_command_id),
    CONSTRAINT ck_chat_moderation_review_fact_kind CHECK (
        review_kind IN ('OPENED', 'REVIEWED', 'REVERSED')),
    CONSTRAINT ck_chat_moderation_review_fact_reason CHECK (btrim(reason) <> '')
);

CREATE TABLE public.chat_moderation_decision_fact (
    decision_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    decision_kind varchar(24) COLLATE "C" NOT NULL,
    reason varchar(280) NOT NULL,
    client_command_id uuid NOT NULL,
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_decision_fact PRIMARY KEY (decision_fact_id),
    CONSTRAINT uq_chat_moderation_decision_fact_command UNIQUE (actor_identity_id, client_command_id),
    CONSTRAINT ck_chat_moderation_decision_fact_kind CHECK (
        decision_kind IN ('NO_ACTION', 'REMOVE', 'ESCALATE')),
    CONSTRAINT ck_chat_moderation_decision_fact_reason CHECK (btrim(reason) <> '')
);

-- Removal is a historical fact; the public marker lives in the projection.
CREATE TABLE public.chat_message_removal_fact (
    removal_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    message_id uuid NOT NULL REFERENCES public.chat_message (message_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    public_marker varchar(32) COLLATE "C" NOT NULL DEFAULT 'MESSAGE_REMOVED',
    reason varchar(280) NOT NULL,
    removed_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    change_cursor bigint GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT pk_chat_message_removal_fact PRIMARY KEY (removal_fact_id),
    CONSTRAINT uq_chat_message_removal_fact_case UNIQUE (case_id),
    CONSTRAINT uq_chat_message_removal_fact_message UNIQUE (message_id),
    CONSTRAINT uq_chat_message_removal_fact_cursor UNIQUE (change_cursor),
    CONSTRAINT ck_chat_message_removal_fact_marker CHECK (public_marker = 'MESSAGE_REMOVED'),
    CONSTRAINT ck_chat_message_removal_fact_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_chat_message_removal_fact_cursor CHECK (change_cursor > 0)
);

CREATE TABLE public.chat_moderation_forward_fact (
    forward_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    reason varchar(280) NOT NULL,
    client_command_id uuid NOT NULL,
    forwarded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_forward_fact PRIMARY KEY (forward_fact_id),
    CONSTRAINT uq_chat_moderation_forward_fact_case UNIQUE (case_id),
    CONSTRAINT uq_chat_moderation_forward_fact_command UNIQUE (actor_identity_id, client_command_id),
    CONSTRAINT ck_chat_moderation_forward_fact_reason CHECK (btrim(reason) <> '')
);

-- Reserved for the later global suspension producer; no automatic sanction is created here.
CREATE TABLE public.chat_moderation_sanction_fact (
    sanction_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    target_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    sanction_kind varchar(32) COLLATE "C" NOT NULL,
    reason varchar(280) NOT NULL,
    client_command_id uuid NOT NULL,
    effective_from timestamptz(6) NOT NULL,
    effective_until timestamptz(6),
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_sanction_fact PRIMARY KEY (sanction_fact_id),
    CONSTRAINT uq_chat_moderation_sanction_fact_command UNIQUE (actor_identity_id, client_command_id),
    CONSTRAINT ck_chat_moderation_sanction_fact_kind CHECK (
        sanction_kind IN ('CHAT_SUSPENSION_24H', 'CHAT_SUSPENSION_7D', 'CHAT_BAN', 'LIFT')),
    CONSTRAINT ck_chat_moderation_sanction_fact_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_chat_moderation_sanction_fact_period CHECK (
        effective_until IS NULL OR effective_until > effective_from)
);

CREATE TABLE public.chat_moderation_evidence_access_fact (
    access_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    case_id uuid NOT NULL REFERENCES public.chat_moderation_case (case_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    access_reason varchar(280) NOT NULL,
    accessed_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_moderation_evidence_access_fact PRIMARY KEY (access_fact_id),
    CONSTRAINT ck_chat_moderation_evidence_access_fact_reason CHECK (btrim(access_reason) <> '')
);

CREATE INDEX idx_chat_moderation_forward_fact_queue
    ON public.chat_moderation_forward_fact (forwarded_at, case_id);
CREATE INDEX idx_chat_moderation_evidence_access_fact_case
    ON public.chat_moderation_evidence_access_fact (case_id, accessed_at, access_fact_id);

CREATE FUNCTION public.prevent_chat_moderation_fact_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'chat moderation history is immutable: UPDATE and DELETE are prohibited';
END $$;

CREATE TRIGGER trg_chat_moderation_case_report_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_case_report_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_moderation_review_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_review_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_moderation_decision_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_decision_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_message_removal_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_message_removal_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_moderation_forward_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_forward_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_moderation_sanction_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_sanction_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();
CREATE TRIGGER trg_chat_moderation_evidence_access_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_moderation_evidence_access_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_moderation_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('REVOKE ALL ON TABLE public.chat_moderation_case, public.chat_moderation_case_report_fact, public.chat_moderation_review_fact, public.chat_moderation_decision_fact, public.chat_message_removal_fact, public.chat_moderation_forward_fact, public.chat_moderation_sanction_fact, public.chat_moderation_evidence_access_fact FROM PUBLIC');
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_moderation_case TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.chat_moderation_case_report_fact, public.chat_moderation_review_fact, public.chat_moderation_decision_fact, public.chat_message_removal_fact, public.chat_moderation_forward_fact, public.chat_moderation_sanction_fact, public.chat_moderation_evidence_access_fact TO %I', runtime_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE public.chat_message_removal_fact_change_cursor_seq TO %I', runtime_role);
END $$;
