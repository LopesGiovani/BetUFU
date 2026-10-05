-- Reports and command receipts are history; account mute state is a preference.
CREATE TABLE public.chat_report (
    report_id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    reporter_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    message_id uuid NOT NULL REFERENCES public.chat_message (message_id),
    reason varchar(32) COLLATE "C" NOT NULL,
    reported_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_chat_report_occurrence UNIQUE (reporter_identity_id, message_id),
    CONSTRAINT uq_chat_report_actor UNIQUE (report_id, reporter_identity_id),
    CONSTRAINT ck_chat_report_reason CHECK (
        reason IN ('DISCURSO_DE_ODIO', 'SPAM', 'OFENSA', 'ASSEDIO', 'FRAUDE'))
);

CREATE INDEX idx_chat_report_message ON public.chat_report (message_id, reported_at, report_id);

CREATE TABLE public.chat_report_receipt (
    reporter_identity_id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    report_id uuid NOT NULL,
    CONSTRAINT pk_chat_report_receipt PRIMARY KEY (reporter_identity_id, idempotency_key),
    CONSTRAINT fk_chat_report_receipt_actor FOREIGN KEY (report_id, reporter_identity_id)
        REFERENCES public.chat_report (report_id, reporter_identity_id)
);

CREATE INDEX idx_chat_report_receipt_report ON public.chat_report_receipt (report_id);

CREATE TABLE public.chat_mute_account (
    owner_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id) PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_chat_mute_version CHECK (version >= 0)
);

CREATE TABLE public.chat_mute (
    owner_identity_id uuid NOT NULL REFERENCES public.chat_mute_account (owner_identity_id),
    participant_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    CONSTRAINT pk_chat_mute PRIMARY KEY (owner_identity_id, participant_id),
    CONSTRAINT ck_chat_mute_not_self CHECK (owner_identity_id <> participant_id)
);

CREATE TRIGGER trg_chat_report_immutable
BEFORE UPDATE OR DELETE ON public.chat_report
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_fact_modification();
CREATE TRIGGER trg_chat_report_receipt_immutable
BEFORE UPDATE OR DELETE ON public.chat_report_receipt
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    REVOKE ALL ON TABLE public.chat_report, public.chat_report_receipt,
        public.chat_mute_account, public.chat_mute FROM PUBLIC;
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.chat_report, public.chat_report_receipt FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.chat_report, public.chat_report_receipt TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_mute_account TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, DELETE ON TABLE public.chat_mute TO %I', runtime_role);
END $$;
