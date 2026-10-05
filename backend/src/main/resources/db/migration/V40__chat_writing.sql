-- Historical accepted receipts are immutable; the per-actor limiter is rebuildable state.
CREATE TABLE public.chat_write_receipt (
    author_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    idempotency_key uuid NOT NULL,
    message_id uuid NOT NULL REFERENCES public.chat_message (message_id),
    CONSTRAINT pk_chat_write_receipt PRIMARY KEY (author_identity_id, idempotency_key),
    CONSTRAINT uq_chat_write_receipt_message UNIQUE (message_id)
);

CREATE TABLE public.chat_write_limiter (
    author_identity_id uuid NOT NULL REFERENCES public.local_identity (identity_id),
    last_accepted_at timestamptz(6),
    CONSTRAINT pk_chat_write_limiter PRIMARY KEY (author_identity_id)
);

CREATE TRIGGER trg_chat_write_receipt_immutable
BEFORE UPDATE OR DELETE ON public.chat_write_receipt
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
    EXECUTE format('REVOKE ALL ON TABLE public.chat_write_receipt, public.chat_write_limiter FROM PUBLIC');
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.chat_write_receipt FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.chat_write_receipt TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_write_limiter TO %I', runtime_role);
END $$;
