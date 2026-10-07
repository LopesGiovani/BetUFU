-- Account-scoped rules dismissal is a preference, never legal consent or mute state.
CREATE TABLE public.chat_rules_dismissal (
    owner_identity_id uuid NOT NULL REFERENCES public.local_identity(identity_id),
    dismissed_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_rules_dismissal PRIMARY KEY (owner_identity_id)
);

-- The first server timestamp is immutable; retries converge through the primary key.
CREATE TRIGGER trg_chat_rules_dismissal_immutable
BEFORE UPDATE OR DELETE ON public.chat_rules_dismissal
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
    REVOKE ALL ON TABLE public.chat_rules_dismissal FROM PUBLIC;
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.chat_rules_dismissal FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.chat_rules_dismissal TO %I', runtime_role);
END $$;
